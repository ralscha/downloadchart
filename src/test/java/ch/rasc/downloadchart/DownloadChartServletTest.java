/*
 * Copyright the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.rasc.downloadchart;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Map;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ContentDisposition;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class DownloadChartServletTest {

	private final DownloadChartServlet servlet = new DownloadChartServlet();

	@Test
	void preservesPngBytesAndSetsDownloadHeaders() throws Exception {
		byte[] png = encode(image(40, 20), "png");
		var response = post(dataUrl(png, "png"), Map.of());
		assertEquals(200, response.getStatus());
		assertArrayEquals(png, response.getContentAsByteArray());
		assertEquals("image/png", response.getContentType());
		assertEquals("chart.png", ContentDisposition.parse(response.getHeader("Content-Disposition")).getFilename());
		assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
		assertEquals("no-store", response.getHeader("Cache-Control"));
	}

	@ParameterizedTest
	@CsvSource({"png,image/png", "gif,image/gif", "jpeg,image/jpeg", "JPG,image/jpeg"})
	void exportsRasterFormats(String format, String contentType) throws Exception {
		var response = post(image(40, 20), Map.of("format", format, "width", "20"));
		assertEquals(200, response.getStatus());
		assertEquals(contentType, response.getContentType());
		BufferedImage result = ImageIO.read(new ByteArrayInputStream(response.getContentAsByteArray()));
		assertNotNull(result);
		assertEquals(20, result.getWidth());
		assertEquals(10, result.getHeight());
	}

	@Test
	void convertsJpegToRealPngEvenWithoutResizing() throws Exception {
		var rgb = new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB);
		var response = post(dataUrl(encode(rgb, "jpeg"), "jpeg"), Map.of());
		byte[] result = response.getContentAsByteArray();
		assertArrayEquals(new byte[] {(byte) 137, 80, 78, 71}, java.util.Arrays.copyOf(result, 4));
	}

	@Test
	void scalesAndPreservesAspectRatioWithoutRoundingToZero() throws Exception {
		var scaled = post(image(40, 20), Map.of("height", "10", "scale", "2"));
		BufferedImage result = ImageIO.read(new ByteArrayInputStream(scaled.getContentAsByteArray()));
		assertEquals(40, result.getWidth());
		assertEquals(20, result.getHeight());
		var tiny = post(image(100, 1), Map.of("width", "1"));
		result = ImageIO.read(new ByteArrayInputStream(tiny.getContentAsByteArray()));
		assertEquals(1, result.getHeight());
	}

	@Test
	void preservesTransparencyWhenResizingPng() throws Exception {
		var source = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
		var response = post(source, Map.of("scale", "2"));
		BufferedImage result = ImageIO.read(new ByteArrayInputStream(response.getContentAsByteArray()));
		assertEquals(0, result.getRGB(5, 5) >>> 24);
	}

	@Test
	void preservesIndexedColorsWhenResizing() throws Exception {
		var palette = new IndexColorModel(1, 2, new byte[] {(byte) 255, 0},
				new byte[] {0, (byte) 255}, new byte[] {0, 0});
		var source = new BufferedImage(2, 1, BufferedImage.TYPE_BYTE_INDEXED, palette);
		source.getRaster().setSample(1, 0, 0, 1);
		var response = post(dataUrl(encode(source, "gif"), "gif"), Map.of("width", "4"));
		BufferedImage result = ImageIO.read(new ByteArrayInputStream(response.getContentAsByteArray()));
		assertEquals(Color.RED.getRGB(), result.getRGB(0, 0));
		assertEquals(Color.GREEN.getRGB(), result.getRGB(3, 0));
	}

	@Test
	void compositesTransparentJpegOnWhite() throws Exception {
		var response = post(new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB),
				Map.of("format", "jpeg"));
		BufferedImage result = ImageIO.read(new ByteArrayInputStream(response.getContentAsByteArray()));
		assertEquals(Color.WHITE.getRGB(), result.getRGB(10, 10));
	}

	@Test
	void honorsZeroJpegQuality() throws Exception {
		BufferedImage source = image(80, 40);
		var low = post(source, Map.of("format", "jpeg", "jpeg", "{\"quality\":0}"));
		var high = post(source, Map.of("format", "jpeg", "jpeg", "{\"quality\":100}"));
		assertEquals(200, low.getStatus());
		assertTrue(low.getContentAsByteArray().length < high.getContentAsByteArray().length);
	}

	@Test
	void sanitizesAndPreservesUnicodeFilename() throws Exception {
		var response = post(image(2, 2), Map.of("filename", "résumé/\"a\r\n.png"));
		String header = response.getHeader("Content-Disposition");
		assertFalse(header.contains("\r"));
		assertFalse(header.contains("\n"));
		assertTrue(header.contains("filename*=UTF-8''"));
		assertEquals("résumé__a__.png", ContentDisposition.parse(header).getFilename());
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"garbage", "data:image/png;base64,!", "data:image/png;base64,",
			"data:image/png;base64,dGV4dA==", "data:image/svg+xml;base64,PHN2Zy8+"})
	void rejectsInvalidImageData(String data) throws Exception {
		assertEquals(400, post(data, Map.of()).getStatus());
	}

	@Test
	void rejectsMismatchedImageType() throws Exception {
		assertEquals(400, post(dataUrl(encode(image(2, 2), "png"), "jpeg"), Map.of()).getStatus());
	}

	@ParameterizedTest
	@CsvSource({"width,0", "height,-1", "width,8193", "width,2147483647", "height,abc",
			"scale,0", "scale,-1", "scale,NaN", "scale,Infinity", "scale,17", "format,bmp"})
	void rejectsInvalidParameters(String parameter, String value) throws Exception {
		var response = post(image(2, 2), Map.of(parameter, value));
		assertEquals(400, response.getStatus());
		assertNull(response.getHeader("Content-Disposition"));
	}

	@Test
	void limitsOutputPixelCountAndScaledDimensions() throws Exception {
		assertEquals(400, post(image(2, 2), Map.of("width", "5000", "height", "5000")).getStatus());
		assertEquals(400, post(image(2, 2), Map.of("width", "8192", "scale", "2")).getStatus());
	}

	@Test
	void rejectsOversizedInputBeforeDecodingRaster() throws Exception {
		byte[] png = encode(image(1, 1), "png");
		ByteBuffer.wrap(png, 16, 4).putInt(8193);
		var crc = new CRC32();
		crc.update(png, 12, 17);
		ByteBuffer.wrap(png, 29, 4).putInt((int) crc.getValue());
		var response = post(dataUrl(png, "png"), Map.of());
		assertEquals(400, response.getStatus());
		assertTrue(response.getErrorMessage().contains("8192"));
	}

	@Test
	void rejectsOversizedEncodedInput() throws Exception {
		assertEquals(400, post("data:image/png;base64," + "A".repeat(12 * 1024 * 1024), Map.of()).getStatus());
	}

	@ParameterizedTest
	@ValueSource(strings = {"{", "[]", "{\"quality\":-1}", "{\"quality\":101}", "{\"quality\":\"bad\"}",
			"{\"quality\":1.5}", "{\"quality\":85} {}"})
	void rejectsInvalidJpegOptions(String options) throws Exception {
		assertEquals(400, post(image(2, 2), Map.of("format", "jpeg", "jpeg", options)).getStatus());
	}

	@ParameterizedTest
	@ValueSource(strings = {"{", "{\"width\":\"10cm\"}", "{\"width\":\"0pt\",\"height\":\"10pt\"}",
			"{\"width\":\"NaNpx\",\"height\":\"10px\"}", "{\"border\":\"-1mm\"}",
			"{\"border\":\"100cm\"}", "{\"border\":\"10foo\"}", "{\"format\":\"A0\"}",
			"{\"orientation\":\"diagonal\"}", "{\"width\":\"20000pt\",\"height\":\"20pt\"}"})
	void rejectsInvalidPdfOptions(String options) throws Exception {
		assertEquals(400, post(image(2, 2), Map.of("format", "pdf", "pdf", options)).getStatus());
	}

	@ParameterizedTest
	@ValueSource(strings = {"portrait", "landscape"})
	void fitsTallImageWithinBothPdfDimensions(String orientation) throws Exception {
		var source = new BufferedImage(200, 1000, BufferedImage.TYPE_INT_RGB);
		var graphics = source.createGraphics();
		try {
			graphics.setColor(Color.RED);
			graphics.fillRect(0, 0, 200, 500);
			graphics.setColor(Color.BLUE);
			graphics.fillRect(0, 500, 200, 500);
		}
		finally {
			graphics.dispose();
		}
		String options = "{\"width\":\"80pt\",\"height\":\"100pt\",\"border\":\"10pt\","
				+ "\"orientation\":\"" + orientation + "\"}";
		var response = post(source, Map.of("format", "pdf", "pdf", options));
		assertEquals(200, response.getStatus());
		assertEquals("application/pdf", response.getContentType());
		try (PDDocument document = Loader.loadPDF(response.getContentAsByteArray())) {
			assertEquals(1, document.getNumberOfPages());
			var page = document.getPage(0);
			assertEquals("landscape".equals(orientation) ? 100 : 80, page.getMediaBox().getWidth());
			BufferedImage result = new PDFRenderer(document).renderImage(0);
			assertEquals(Color.RED.getRGB(), result.getRGB(15, 11));
			assertEquals(Color.BLUE.getRGB(), result.getRGB(15, result.getHeight() - 12));
			assertEquals(Color.WHITE.getRGB(), result.getRGB(15, result.getHeight() - 5));
		}
	}

	@ParameterizedTest
	@CsvSource({"A3,841.8898,1190.5511", "a4,595.2756,841.8898", "A5,419.5276,595.2756",
			"Letter,612,792", "Legal,612,1008", "Tabloid,792,1224"})
	void supportsNamedPdfPageSizes(String format, float width, float height) throws Exception {
		var response = post(image(2, 2), Map.of("format", "pdf", "pdf", "{\"format\":\"" + format + "\"}"));
		try (PDDocument document = Loader.loadPDF(response.getContentAsByteArray())) {
			assertEquals(width, document.getPage(0).getMediaBox().getWidth(), 0.01f);
			assertEquals(height, document.getPage(0).getMediaBox().getHeight(), 0.01f);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"25.4mm", "2.54cm", "1in", "72px", "72pt", "72"})
	void convertsPdfUnitsAndLetsCustomSizeOverrideFormat(String length) throws Exception {
		String options = "{\"format\":\"Letter\",\"width\":\"" + length + "\",\"height\":\"" + length + "\"}";
		var response = post(image(2, 2), Map.of("format", "pdf", "pdf", options));
		try (PDDocument document = Loader.loadPDF(response.getContentAsByteArray())) {
			assertEquals(72, document.getPage(0).getMediaBox().getWidth(), 0.01f);
			assertEquals(72, document.getPage(0).getMediaBox().getHeight(), 0.01f);
		}
	}

	private MockHttpServletResponse post(BufferedImage image, Map<String, String> parameters) throws Exception {
		return post(dataUrl(encode(image, "png"), "png"), parameters);
	}

	private MockHttpServletResponse post(String data, Map<String, String> parameters) throws Exception {
		var request = new MockHttpServletRequest("POST", "/downloadchart");
		if (data != null) {
			request.setParameter("data", data);
		}
		parameters.forEach(request::setParameter);
		var response = new MockHttpServletResponse();
		this.servlet.doPost(request, response);
		return response;
	}

	private static BufferedImage image(int width, int height) {
		var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				image.setRGB(x, y, new Color(x * 31 % 256, y * 47 % 256, (x + y) * 71 % 256).getRGB());
			}
		}
		return image;
	}

	private static byte[] encode(BufferedImage image, String format) throws Exception {
		var output = new ByteArrayOutputStream();
		assertTrue(ImageIO.write(image, format, output));
		return output.toByteArray();
	}

	private static String dataUrl(byte[] bytes, String format) {
		return "data:image/" + format + ";base64," + Base64.getEncoder().encodeToString(bytes);
	}
}
