package ch.rasc.downloadchart;

public class PdfOptions {
	public String width;
	public String height;
	public String border;

	public String format;
	public String orientation;
}

/*
 * PDF specific options. This config is only used if config.format is set to 'pdf'. The given object should be in either this format:

{
  width: '200px',
  height: '300px',
  border: '0px'
}
or this format:

{
  format: 'A4',
  orientation: 'portrait',
  border: '1cm'
}
Supported dimension units are: 'mm', 'cm', 'in', 'px'. No unit means 'px'. Supported formats are: 'A3', 'A4', 'A5', 'Legal', 'Letter', 'Tabloid'. Orientation ('portrait', 'landscape') is optional and defaults to 'portrait'.
*/