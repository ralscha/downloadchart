Ext.define('DrawContainerOverride', {
	override: 'Ext.draw.Container',

	download: function(config) {
		var me = this, inputs = [], name, value, form

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

		form = Ext.dom.Helper.append(Ext.getBody(), {
			id: 'chartDownloadForm',
			tag: 'form',
			method: 'POST',
			action: config.url || me.defaultDownloadServerUrl,
			children: inputs
		});
		form.submit();
		Ext.fly('chartDownloadForm').destroy();
	}
});
