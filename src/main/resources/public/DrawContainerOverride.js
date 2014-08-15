Ext.define('DrawContainerOverride', {
	override: 'Ext.draw.Container',

	download: function(config) {
		var me = this, inputs = [], markup, name, value;

		config = Ext.apply({
			version: 2,
			data: me.getImage().data
		}, config);

		for (name in config) {
			if (config.hasOwnProperty(name)) {
				value = config[name];
				if (name in me.supportedOptions) {
					if (me.supportedOptions[name].call(me, value)) {
						inputs.push({
							tag: 'input',
							type: 'hidden',
							name: name,
							value: Ext.isObject(value) ? Ext.String.htmlEncode(Ext.JSON.encode(value)) : value
						});
					}
				}
			}
		}

		markup = Ext.dom.Helper.markup({
			tag: 'html',
			children: [ {
				tag: 'head'
			}, {
				tag: 'body',
				children: [ {
					tag: 'form',
					method: 'POST',
					action: config.url || me.defaultDownloadServerUrl,
					children: inputs
				}, {
					tag: 'script',
					type: 'text/javascript',
					children: 'document.getElementsByTagName("form")[0].submit();'
				} ]
			} ]
		});

		window.open('', 'ImageDownload_' + Date.now()).document.write(markup);
	}
});
