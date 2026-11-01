Ext.define('DrawContainerOverride', {
	override: 'Ext.draw.Container',

	download: function(config) {
		var me = this, form = document.createElement('form');

		config = Ext.apply({
			version: 2,
			data: me.getImage().data
		}, config);

		Object.keys(config).forEach(function(name) {
			var value = config[name], input;
			if (Object.prototype.hasOwnProperty.call(me.supportedOptions, name) &&
					me.supportedOptions[name].call(me, value)) {
				input = document.createElement('input');
				input.type = 'hidden';
				input.name = name;
				// Assign DOM properties so filenames and JSON cannot become HTML.
				input.value = Ext.isObject(value) ? Ext.JSON.encode(value) : value;
				form.appendChild(input);
			}
		});

		form.method = 'POST';
		form.acceptCharset = 'UTF-8';
		form.action = config.url || me.defaultDownloadServerUrl;
		form.style.display = 'none';
		document.body.appendChild(form);
		try {
			form.submit();
		}
		finally {
			form.remove();
		}
	}
});
