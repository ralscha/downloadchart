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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.http.ContentDisposition;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

public class DownloadChartServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder()
			.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
			.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
			.build();
	private static final Set<String> IMAGE_FORMATS = Set.of("png", "jpeg", "gif");
	private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
	private static final int MAX_DIMENSION = 8192;
	private static final long MAX_PIXELS = 16_000_000;
	private static final float POINTS_PER_INCH = 72;

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		request.setCharacterEncoding(StandardCharsets.UTF_8);
		try {
			String format = normalizeFormat(parameter(request, "format", "png"));
			if (!IMAGE_FORMATS.contains(format) && !"pdf".equals(format)) {
				throw new IllegalArgumentException("Unsupported format. Use png, jpeg, gif or pdf.");
			}
			Integer width = dimensionParameter(request, "width");
			Integer height = dimensionParameter(request, "height");
			double scale = Double.parseDouble(parameter(request, "scale", "1"));
			if (!Double.isFinite(scale) || scale <= 0 || scale > 16) {
				throw new IllegalArgumentException("Scale must be greater than 0 and at most 16.");
			}
			JpegOptions jpeg = "jpeg".equals(format)
					? readOptions(request, "jpeg", JpegOptions.class) : null;
			int quality = jpeg == null || jpeg.quality == null ? 100 : jpeg.quality;
			if (quality < 0 || quality > 100) {
				throw new IllegalArgumentException("JPEG quality must be between 0 and 100.");
			}
			PdfLayout pdf = "pdf".equals(format)
					? pdfLayout(readOptions(request, "pdf", PdfOptions.class)) : null;
			ImageData source = readImage(request.getParameter("data"));
			Dimension size = calculateDimension(source.image(), width, height, scale);
			String extension = "jpeg".equals(format) ? "jpg" : format;
			String filename = filename(parameter(request, "filename", "chart"), extension);

			response.setContentType("pdf".equals(format) ? "application/pdf" : "image/" + format);
			response.setHeader("Content-Disposition", ContentDisposition.attachment()
					.filename(filename, StandardCharsets.UTF_8).build().toString());
			response.setHeader("X-Content-Type-Options", "nosniff");
			response.setHeader("Cache-Control", "no-store");
			switch (format) {
				case "pdf" -> writePdf(response, source, size, pdf);
				case "jpeg" -> writeJpeg(response, resize(source.image(), size, true), quality);
				case "png", "gif" -> {
					if ("png".equals(format) && format.equals(source.format())
							&& sameSize(source.image(), size)) {
						// Preserve the original PNG without another encoding pass.
						response.getOutputStream().write(source.bytes());
					}
					else if (!ImageIO.write(resize(source.image(), size, false), format,
							response.getOutputStream())) {
						throw new IOException("No image writer for " + format);
					}
				}
				default -> throw new IllegalStateException("Unexpected format: " + format);
			}
		}
		catch (IllegalArgumentException ex) {
			response.reset();
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
		}
	}

	private static String parameter(HttpServletRequest request, String name, String fallback) {
		String value = request.getParameter(name);
		return value == null || value.isBlank() ? fallback : value.trim();
	}

	private static String normalizeFormat(String format) {
		String normalized = format.toLowerCase(Locale.ROOT);
		return "jpg".equals(normalized) ? "jpeg" : normalized;
	}

	private static Integer dimensionParameter(HttpServletRequest request, String name) {
		String value = parameter(request, name, null);
		if (value == null) {
			return null;
		}
		int dimension = Integer.parseInt(value);
		if (dimension < 1 || dimension > MAX_DIMENSION) {
			throw new IllegalArgumentException(name + " must be between 1 and " + MAX_DIMENSION + ".");
		}
		return dimension;
	}

	private static <T> T readOptions(HttpServletRequest request, String name, Class<T> type) {
		String value = parameter(request, name, null);
		if (value == null) {
			return null;
		}
		if (value.length() > 4096) {
			throw new IllegalArgumentException("Options are too long: " + name);
		}
		try {
			return OBJECT_MAPPER.readValue(value, type);
		}
		catch (JacksonException ex) {
			throw new IllegalArgumentException("Invalid JSON options: " + name, ex);
		}
	}

	private static ImageData readImage(String dataUrl) {
		if (dataUrl == null || dataUrl.length() > 4 * ((MAX_IMAGE_BYTES + 2) / 3) + 32) {
			throw new IllegalArgumentException("An image data URL of at most 8 MiB is required.");
		}
		int comma = dataUrl.indexOf(',');
		if (comma < 0 || !dataUrl.substring(0, comma).toLowerCase(Locale.ROOT)
				.matches("data:image/(png|jpe?g|gif);base64")) {
			throw new IllegalArgumentException("Data must be a base64 PNG, JPEG or GIF data URL.");
		}
		String declaredFormat = normalizeFormat(dataUrl.substring(11, dataUrl.indexOf(';')));
		byte[] bytes = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
		if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
			throw new IllegalArgumentException("Image data must contain between 1 byte and 8 MiB.");
		}
		try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
			var readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				throw new IllegalArgumentException("Image data could not be decoded.");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				String actualFormat = normalizeFormat(reader.getFormatName());
				if (!declaredFormat.equals(actualFormat)) {
					throw new IllegalArgumentException("The image type does not match its data URL.");
				}
				// Inspect dimensions before allocating the decompressed raster.
				checkDimensions(reader.getWidth(0), reader.getHeight(0));
				BufferedImage image = reader.read(0);
				return new ImageData(bytes, image, actualFormat);
			}
			finally {
				reader.dispose();
			}
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Image data could not be decoded.", ex);
		}
	}

	private static Dimension calculateDimension(BufferedImage image, Integer width,
			Integer height, double scale) {
		double targetWidth = width == null ? image.getWidth() : width;
		double targetHeight = height == null ? image.getHeight() : height;
		if (width == null && height != null) {
			targetWidth = (double) image.getWidth() * height / image.getHeight();
		}
		else if (width != null && height == null) {
			targetHeight = (double) image.getHeight() * width / image.getWidth();
		}
		long scaledWidth = Math.max(1, Math.round(targetWidth * scale));
		long scaledHeight = Math.max(1, Math.round(targetHeight * scale));
		checkDimensions(scaledWidth, scaledHeight);
		return new Dimension((int) scaledWidth, (int) scaledHeight);
	}

	private static void checkDimensions(long width, long height) {
		if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION
				|| width * height > MAX_PIXELS) {
			throw new IllegalArgumentException("Images must be at most 8192 pixels per side and 16 megapixels.");
		}
	}

	private static String filename(String value, String extension) {
		String safe = value.replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", "_");
		// Limit by code point so a supplementary Unicode character is never split.
		safe = safe.substring(0, safe.offsetByCodePoints(0, Math.min(120,
				safe.codePointCount(0, safe.length()))));
		if (safe.isBlank() || ".".equals(safe) || "..".equals(safe)) {
			safe = "chart";
		}
		return safe.toLowerCase(Locale.ROOT).endsWith("." + extension)
				? safe : safe + "." + extension;
	}

	private static boolean sameSize(BufferedImage image, Dimension size) {
		return image.getWidth() == size.width && image.getHeight() == size.height;
	}

	private static BufferedImage resize(BufferedImage image, Dimension size, boolean opaque) {
		if (!opaque && sameSize(image, size)) {
			return image;
		}
		BufferedImage resized = new BufferedImage(size.width, size.height,
				opaque ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = resized.createGraphics();
		try {
			if (opaque) {
				graphics.setColor(Color.WHITE);
				graphics.fillRect(0, 0, size.width, size.height);
			}
			else {
				graphics.setComposite(AlphaComposite.Src);
			}
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
					RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.drawImage(image, 0, 0, size.width, size.height, null);
		}
		finally {
			graphics.dispose();
		}
		return resized;
	}

	private static void writeJpeg(HttpServletResponse response, BufferedImage image,
			int quality) throws IOException {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		try (var output = new MemoryCacheImageOutputStream(response.getOutputStream())) {
			ImageWriteParam parameters = writer.getDefaultWriteParam();
			parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			parameters.setCompressionQuality(quality / 100f);
			writer.setOutput(output);
			writer.write(null, new IIOImage(image, null, null), parameters);
		}
		finally {
			writer.dispose();
		}
	}

	private static PdfLayout pdfLayout(PdfOptions options) {
		PDRectangle page = PDRectangle.A4;
		float border = 0;
		if (options != null) {
			if ((options.width == null) != (options.height == null)) {
				throw new IllegalArgumentException("Specify both PDF width and height.");
			}
			if (options.width != null) {
				page = new PDRectangle(points(options.width), points(options.height));
			}
			else if (options.format != null && !options.format.isBlank()) {
				page = switch (options.format.trim().toLowerCase(Locale.ROOT)) {
					case "a3" -> PDRectangle.A3;
					case "a4" -> PDRectangle.A4;
					case "a5" -> PDRectangle.A5;
					case "letter" -> PDRectangle.LETTER;
					case "legal" -> PDRectangle.LEGAL;
					case "tabloid" -> new PDRectangle(11 * POINTS_PER_INCH, 17 * POINTS_PER_INCH);
					default -> throw new IllegalArgumentException("Unsupported PDF page format.");
				};
			}
			if (options.orientation != null && !options.orientation.isBlank()) {
				String orientation = options.orientation.trim().toLowerCase(Locale.ROOT);
				if (!"portrait".equals(orientation) && !"landscape".equals(orientation)) {
					throw new IllegalArgumentException("PDF orientation must be portrait or landscape.");
				}
				boolean landscape = "landscape".equals(orientation);
				if (landscape != (page.getWidth() > page.getHeight())) {
					page = new PDRectangle(/* width= */ page.getHeight(), /* height= */ page.getWidth());
				}
			}
			border = options.border == null ? 0 : points(options.border);
		}
		if (page.getWidth() <= 0 || page.getHeight() <= 0
				|| page.getWidth() > 14400 || page.getHeight() > 14400
				|| border * 2 >= Math.min(page.getWidth(), page.getHeight())) {
			throw new IllegalArgumentException("Invalid PDF page size or border.");
		}
		return new PdfLayout(page, border);
	}

	private static float points(String value) {
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		float multiplier = 1;
		if (normalized.endsWith("mm") || normalized.endsWith("cm")
				|| normalized.endsWith("in") || normalized.endsWith("px") || normalized.endsWith("pt")) {
			String unit = normalized.substring(normalized.length() - 2);
			multiplier = switch (unit) {
				case "mm" -> POINTS_PER_INCH / 25.4f;
				case "cm" -> POINTS_PER_INCH / 2.54f;
				case "in" -> POINTS_PER_INCH;
				default -> 1; // Retain the service's historical 1 px = 1 PDF point.
			};
			normalized = normalized.substring(0, normalized.length() - 2).trim();
		}
		float result = Float.parseFloat(normalized) * multiplier;
		if (!Float.isFinite(result) || result < 0) {
			throw new IllegalArgumentException("PDF lengths must be finite and non-negative.");
		}
		return result;
	}

	private static void writePdf(HttpServletResponse response, ImageData source,
			Dimension size, PdfLayout layout) throws IOException {
		try (PDDocument document = new PDDocument()) {
			PDPage page = new PDPage(layout.page());
			document.addPage(page);
			PDImageXObject image = "jpeg".equals(source.format())
					? JPEGFactory.createFromByteArray(document, source.bytes())
					: LosslessFactory.createFromImage(document, source.image());
			float border = layout.border();
			float scale = Math.min(1, Math.min((layout.page().getWidth() - 2 * border) / size.width,
					(layout.page().getHeight() - 2 * border) / size.height));
			float height = size.height * scale;
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				content.drawImage(image, border, layout.page().getHeight() - border - height,
						size.width * scale, height);
			}
			document.save(response.getOutputStream());
		}
	}

	// Private request-local carrier; no value equality or defensive array copying is needed.
	@SuppressWarnings("ArrayRecordComponent")
	private record ImageData(byte[] bytes, BufferedImage image, String format) {
	}

	private record PdfLayout(PDRectangle page, float border) {
	}
}
