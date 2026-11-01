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

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ContentDisposition;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {"server.servlet.context-path=/charts", "debug=false"})
class ApplicationTest {

	@LocalServerPort
	private int port;

	@Test
	void servesDemoAndExportsThroughRealHttpUnderContextPath() throws Exception {
		try (HttpClient client = HttpClient.newHttpClient()) {
			var home = client.send(HttpRequest.newBuilder(uri("/")).GET().build(),
					HttpResponse.BodyHandlers.ofString());
			assertEquals(200, home.statusCode());
			assertTrue(home.body().contains("Download Chart"));

			var script = client.send(HttpRequest.newBuilder(uri("/app.js")).GET().build(),
					HttpResponse.BodyHandlers.ofString());
			assertEquals(200, script.statusCode());
			assertTrue(script.body().contains("downloadChart"));

			var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
			var output = new ByteArrayOutputStream();
			assertTrue(ImageIO.write(image, "png", output));
			String data = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(output.toByteArray());
			String body = "data=" + URLEncoder.encode(data, StandardCharsets.UTF_8)
					+ "&filename=" + URLEncoder.encode("Grüezi", StandardCharsets.UTF_8);
			var request = HttpRequest.newBuilder(uri("/downloadchart"))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString(body)).build();
			var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
			assertEquals(200, response.statusCode());
			assertArrayEquals(output.toByteArray(), response.body());
			assertEquals("Grüezi.png", ContentDisposition.parse(
					response.headers().firstValue("Content-Disposition").orElseThrow()).getFilename());

			var invalid = client.send(HttpRequest.newBuilder(uri("/downloadchart"))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString("format=bmp")).build(),
					HttpResponse.BodyHandlers.ofString());
			assertEquals(400, invalid.statusCode());
			assertTrue(invalid.headers().firstValue("Content-Disposition").isEmpty());
		}
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + this.port + "/charts" + path);
	}
}
