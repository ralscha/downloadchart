package ch.rasc.downloadchart;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.pdfbox.exceptions.COSVisitorException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.edit.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.xobject.PDPixelMap;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DownloadChartServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private final static String encodingPrefix = "base64,";

	private final static ObjectMapper objectMapper = new ObjectMapper();

	private static final int DEFAULT_USER_SPACE_UNIT_DPI = 72;
	private static final float MM_TO_UNITS = 1 / (10 * 2.54f)
			* DEFAULT_USER_SPACE_UNIT_DPI;

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		String dataUrl = request.getParameter("data");
		int contentStartIndex = dataUrl.indexOf(encodingPrefix) + encodingPrefix.length();
		byte[] imageData = Base64.getDecoder().decode(
				dataUrl.substring(contentStartIndex));

		String format = request.getParameter("format");
		if (format == null) {
			format = "png";
		}

		String filename = request.getParameter("filename");
		if (filename == null) {
			filename = "chart";
		}

		String widthString = request.getParameter("width");
		Integer width = null;
		if (widthString != null) {
			width = Integer.valueOf(widthString);
		}

		String heightString = request.getParameter("height");
		Integer height = null;
		if (heightString != null) {
			height = Integer.valueOf(heightString);
		}

		// String scaleString = request.getParameter("scale");
		// Integer scale = null;
		// if (scaleString != null) {
		// scale = Integer.valueOf(scaleString);
		// }

		if (format.equals("png")) {
			handlePng(response, imageData, width, height, filename);
		}
		else if (format.equals("jpeg")) {
			JpegOptions options = readOptions(request, "jpeg");
			handleJpg(response, imageData, width, height, filename, options);
		}
		else if (format.equals("gif")) {
			handleGif(response, imageData, width, height, filename);
		}
		else if (format.equals("pdf")) {
			PdfOptions options = readOptions(request, "pdf");
			handlePdf(response, imageData, width, height, filename, options);
		}

		response.getOutputStream().flush();
	}

	private static <T> T readOptions(HttpServletRequest request, String requestParameter)
			throws IOException, JsonParseException, JsonMappingException {
		String optionsString = request.getParameter(requestParameter);
		T options = null;
		if (optionsString != null) {
			options = objectMapper.readValue(optionsString, new TypeReference<T>() {
				// nothing_here
			});
		}
		return options;
	}

	private static void handlePng(HttpServletResponse response, byte[] imageData,
			Integer width, Integer height, String filename) throws IOException {
		response.setContentType("image/png");
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename
				+ ".png\";");

		writeImage(response, imageData, width, height, "png");
	}

	private static void handleJpg(HttpServletResponse response, byte[] imageData,
			Integer width, Integer height, String filename, JpegOptions options)
			throws IOException {
		response.setContentType("image/jpeg");
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename
				+ ".jpg\";");

		BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));

		Dimension newDimension = calculateDimension(image, width, height);
		int imgWidth = image.getWidth();
		int imgHeight = image.getHeight();
		if (newDimension != null) {
			imgWidth = newDimension.width;
			imgHeight = newDimension.height;
		}

		BufferedImage newImage = new BufferedImage(imgWidth, imgHeight,
				BufferedImage.TYPE_INT_RGB);

		Graphics2D g = newImage.createGraphics();
		g.drawImage(image, 0, 0, imgWidth, imgHeight, Color.BLACK, null);
		g.dispose();

		if (newDimension != null) {
			g.setComposite(AlphaComposite.Src);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
					RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setRenderingHint(RenderingHints.KEY_RENDERING,
					RenderingHints.VALUE_RENDER_QUALITY);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
		}

		try (ImageOutputStream ios = ImageIO.createImageOutputStream(response
				.getOutputStream())) {
			Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName("jpg");
			ImageWriter writer = iter.next();
			ImageWriteParam iwp = writer.getDefaultWriteParam();
			iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			if (options != null && options.quality != null) {
				iwp.setCompressionQuality(options.quality / 100f);
			}
			else {
				iwp.setCompressionQuality(1);
			}
			writer.setOutput(ios);
			writer.write(null, new IIOImage(newImage, null, null), iwp);
			writer.dispose();
		}
	}

	private static void handleGif(HttpServletResponse response, byte[] imageData,
			Integer width, Integer height, String filename) throws IOException {
		response.setContentType("image/gif");
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename
				+ ".gif\";");

		writeImage(response, imageData, width, height, "gif");
	}

	private static void handlePdf(HttpServletResponse response, byte[] imageData,
			Integer width, Integer height, String filename, PdfOptions options)
			throws IOException {
		response.setContentType("application/pdf");
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename
				+ ".pdf\";");

		try (PDDocument document = new PDDocument()) {
			PDRectangle format = PDPage.PAGE_SIZE_A4;
			if (options != null) {
				if ("A3".equals(options.format)) {
					format = PDPage.PAGE_SIZE_A3;
				}
				else if ("A5".equals(options.format)) {
					format = PDPage.PAGE_SIZE_A5;
				}
				else if ("Letter".equals(options.format)) {
					format = PDPage.PAGE_SIZE_LETTER;
				}
				else if ("Legal".equals(options.format)) {
					format = new PDRectangle(215.9f * MM_TO_UNITS, 355.6f * MM_TO_UNITS);
				}
				else if ("Tabloid".equals(options.format)) {
					format = new PDRectangle(279 * MM_TO_UNITS, 432 * MM_TO_UNITS);
				}
				else if (options.width != null && options.height != null) {
					Float pdfWidth = extractValue(options.width);
					Float pdfHeight = extractValue(options.height);
					if (pdfWidth != null && pdfHeight != null) {
						format = new PDRectangle(pdfWidth, pdfHeight);
					}
				}
			}

			PDPage page = new PDPage(format);

			if (options != null && "landscape".equals(options.orientation)) {
				page.setRotation(90);
			}

			document.addPage(page);

			BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
			// int w = image.getWidth();
			// int h = image.getHeight();
			// BufferedImage after = new BufferedImage(w, h,
			// BufferedImage.TYPE_INT_ARGB);
			// AffineTransform at = new AffineTransform();
			// at.scale(0.8, 0.8);
			// AffineTransformOp scaleOp =
			// new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
			// after = scaleOp.filter(image, after);

			try (PDPageContentStream contentStream = new PDPageContentStream(document,
					page)) {

				System.out.println(page.getMediaBox());

				PDPixelMap ximage = new PDPixelMap(document, image);
				// contentStream.drawImage(ximage, 10, 0);

				float scale = 0.7f; // reduce this value if the image is too large
				contentStream.drawXObject(ximage, 20, page.getMediaBox().getHeight() - 20
						- ximage.getHeight() * scale, ximage.getWidth() * scale,
						ximage.getHeight() * scale);
			}

			try {
				document.save(response.getOutputStream());
			}
			catch (COSVisitorException e) {
				throw new IOException(e);
			}
		}
	}

	private static void writeImage(HttpServletResponse response, byte[] imageData,
			Integer width, Integer height, String formatName) throws IOException {
		BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imageData));
		Dimension newDimension = calculateDimension(originalImage, width, height);
		if (newDimension != null) {
			int type = originalImage.getType() == 0? BufferedImage.TYPE_INT_ARGB : originalImage.getType();
			BufferedImage resizedImage = new BufferedImage(newDimension.width, newDimension.height, type);
			Graphics2D g = resizedImage.createGraphics();
			g.drawImage(originalImage, 0, 0, newDimension.width, newDimension.height, null);
			g.dispose();
			g.setComposite(AlphaComposite.Src);

			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setRenderingHint(RenderingHints.KEY_RENDERING,
			RenderingHints.VALUE_RENDER_QUALITY);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
			RenderingHints.VALUE_ANTIALIAS_ON);

			ImageIO.write(resizedImage, formatName, response.getOutputStream());
		} else {
			if ("png".equals(formatName)) {
				response.getOutputStream().write(imageData);
			} else {
				ImageIO.write(originalImage, "gif", response.getOutputStream());
			}
		}
	}

	private static Dimension calculateDimension(BufferedImage image, Integer width,
			Integer height) {

		int imgWidth = image.getWidth();
		int imgHeight = image.getHeight();

		if (width == null && height != null) {
			return new Dimension(imgWidth * height / imgHeight, height);
		}
		else if (width != null && height == null) {
			return new Dimension(width, imgHeight * width / imgWidth);
		}
		else if (width != null && height != null) {
			return new Dimension(width, height);
		}

		return null;

	}

	private static Float extractValue(String str) {
		if (str.endsWith("mm")) {
			return Float.parseFloat(str.substring(0, str.length() - 2)) * MM_TO_UNITS;
		}
		else if (str.endsWith("cm")) {
			return Float.parseFloat(str.substring(0, str.length() - 2)) * 10
					* MM_TO_UNITS;
		}
		else if (str.endsWith("in")) {
			return Float.parseFloat(str.substring(0, str.length() - 2))
					* DEFAULT_USER_SPACE_UNIT_DPI;
		}
		else if (str.endsWith("px")) {
			return Float.parseFloat(str.substring(0, str.length() - 2));
		}

		return null;
	}
}
