# downloadchart

A small Spring Boot service for downloading Ext JS charts as PNG, JPEG, GIF or
single-page PDF files. It accepts the raster data URLs produced by
`Ext.draw.Container.getImage()`.

## Build and run

Use JDK 25 or newer. The Maven wrapper supplies Maven 3.10.0.

```sh
./mvnw verify
java -jar target/downloadchart.jar
```

On Windows, use `mvnw.cmd verify`. Open <http://localhost:8080> for the demo.
The demo uses the existing Ext JS bundles at `https://static.rasc.ch/dc/`;
its UI requires access to that CDN. The export endpoint works independently.

The Maven build runs image/PDF regression tests, a real HTTP integration test,
Error Prone, and license checks. To run the JavaScript tests, use Node.js 22+
(no npm dependencies are needed):

```sh
node src/test/js/download.test.cjs
```

## API

Send an `application/x-www-form-urlencoded` POST to `/downloadchart`.
Encode each form value correctly, particularly `+` in base64 data.

| Parameter | Meaning |
| --- | --- |
| `data` | Required base64 PNG, JPEG or GIF data URL. The declared type must match the image. |
| `format` | `png` (default), `jpeg` / `jpg`, `gif` or `pdf`; case insensitive. |
| `filename` | Download filename, default `chart`. Unicode is supported; unsafe filename characters are replaced. The output extension is added unless already present. |
| `width`, `height` | Positive integer image dimensions. Specifying only one preserves the aspect ratio; specifying both uses that exact size. |
| `scale` | Multiplier applied after sizing, greater than 0 and at most 16; default 1. |
| `jpeg` | JSON object, for example `{"quality":85}`. Quality accepts the full range 0–100; default 100. Transparent areas become white. |
| `pdf` | JSON object with the options below. |

PDF options:

| Option | Meaning |
| --- | --- |
| `format` | `A3`, `A4` (default), `A5`, `Letter`, `Legal` or `Tabloid`; case insensitive. |
| `width`, `height` | Custom page size; both are required together and override `format`. |
| `orientation` | `portrait` or `landscape`. Named page formats default to portrait; custom sizes retain their supplied orientation unless this is set. |
| `border` | Non-negative margin on all sides, default 0. Must leave space for the chart. |

PDF lengths accept `mm`, `cm`, `in`, `pt`, `px`, or bare numbers (points).
For compatibility, `1px` means one PDF point (1/72 inch).
Pages must have positive dimensions no larger than 14,400 points per side.
Charts align to the top left inside the border and shrink to fit **both** page
dimensions without clipping or automatic enlargement. Image sizing parameters
control the chart's size on the page.

Input images are limited to 8 MiB of decoded file data. Source and output images
are limited to 8,192 pixels per side and 16 million pixels total. Image dimensions
are checked before allocating the decoded raster. The embedded server limits
form bodies to 16 MiB. Invalid requests return HTTP 400 rather than an empty
download or server error. Animated GIF input exports its first frame.

Example using a file containing a complete data URL:

```sh
curl --fail-with-body --output chart.pdf \
  --data-urlencode data@chart-data-url.txt \
  --data-urlencode format=pdf \
  --data-urlencode 'pdf={"format":"A4","orientation":"landscape","border":"10mm"}' \
  http://localhost:8080/downloadchart
```

To use the service from Ext JS, load `DrawContainerOverride.js`, set the chart's
`defaultDownloadServerUrl` to the endpoint, and call:

```js
chart.download({ format: 'png', filename: 'chart', scale: 2 });
```

## Docker

Build the JAR first, then build the runtime image:

```sh
docker build -t downloadchart .
docker run --rm -p 8080:8080 downloadchart
```

Alternatively, compile and verify the project inside the build image:

```sh
docker build -f Dockerfile.Build -t downloadchart .
```

Both images run as an unprivileged user on Eclipse Temurin Java 25.
The old Spotify Maven Docker plugin and Java 8 installation scripts have been
removed; use these Docker commands instead.
