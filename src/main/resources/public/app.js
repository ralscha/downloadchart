Ext.onReady(function() {

	var myDataStore = new Ext.data.JsonStore({
		fields: [ 'complaint', 'count', 'cumnumber', 'cumpercent' ],
		data: [ {
			complaint: 'Overpriced',
			count: 543,
			cumnumber: 543,
			cumpercent: 31
		}, {
			complaint: 'Small Portions',
			count: 412,
			cumnumber: 955,
			cumpercent: 55
		}, {
			complaint: 'High Wait Time',
			count: 245,
			cumnumber: 1200,
			cumpercent: 69
		}, {
			complaint: 'Tasteless Food',
			count: 187,
			cumnumber: 1387,
			cumpercent: 80
		}, {
			complaint: 'Bad Ambiance',
			count: 134,
			cumnumber: 1521,
			cumpercent: 88
		}, {
			complaint: 'Not Clean',
			count: 98,
			cumnumber: 1619,
			cumpercent: 93
		}, {
			complaint: 'Too Noisy',
			count: 65,
			cumnumber: 1684,
			cumpercent: 97
		}, {
			complaint: 'Salty Food',
			count: 41,
			cumnumber: 1725,
			cumpercent: 99
		}, {
			complaint: 'Unfriendly Staff',
			count: 12,
			cumnumber: 1737,
			cumpercent: 100
		} ]
	});

	new Ext.Container({
		renderTo: Ext.getBody(),
		fullscreen: true,
		layout: 'fit',
		title: 'Download Chart Example',
		viewModel: {

		},

		controller: {
			downloadPng: function() {
				this.downloadChart('png');
			},

			downloadPdf: function() {
				this.downloadChart('pdf');
			},
			
			donwloadJpeg: function() {
				this.downloadChart('jpeg');
			},
			
			downloadGif: function() {
				this.downloadChart('gif');
			},
			
			onAxisLabelRender: function(axis, label, layoutContext) {
				var total = axis.getRange()[1];

				return (label / total * 100).toFixed(0) + '%';
			},

			onBarSeriesTooltipRender: function(tooltip, record, item) {
				tooltip.setHtml(record.get('complaint') + ': ' + record.get('count') + ' responses.');
			},

			onLineSeriesTooltipRender: function(tooltip, record, item) {
				var store = record.store, i, complaints = [];

				for (i = 0; i <= item.index; i++) {
					complaints.push(store.getAt(i).get('complaint'));
				}

				tooltip.setHtml('<div style="text-align: center; font-weight: bold">' + record.get('cumpercent') + '%</div>'
						+ complaints.join('<br>'));
			},

			onPercentRender: function(value) {
				return value + '%';
			},

			downloadChart: function(format) {
				var vm = this.getViewModel();
				var filename = vm.get('filename');
				var quality = vm.get('quality');
				var width = vm.get('width');
				var height = vm.get('height');
				var pdf = Ext.clone(vm.get('pdf'));
				var config = Object.create(null);

				if (format !== 'png') {
					config.format = format;
				}

				if (width) {
					config.width = width;
				}

				if (height) {
					config.height = height;
				}

				if (filename) {
					config.filename = filename;
				}

				if (format === 'jpeg' && quality) {
					config.jpeg = {
						quality: quality
					}
				}
				else if (format === 'pdf' && pdf) {
					if (pdf.unit) {
						if (pdf.border) {
							pdf.border = pdf.border + pdf.unit;
						}
						if (pdf.width) {
							pdf.width = pdf.width + pdf.unit;
						}
						if (pdf.height) {
							pdf.height = pdf.height + pdf.unit;
						}
					}
					delete pdf.unit;

					if (pdf.orientation) {
						pdf.orientation = pdf.orientation.orientation;
					}

					config.pdf = pdf;
				}

				console.log(config);

				this.lookup('chart').download(config);
			}
		},


		items: [ {
			xtype: 'cartesian',
			defaultDownloadServerUrl: 'downloadchart',
			shadow: 'true',
			reference: 'chart',
			theme: 'category2',
			store: myDataStore,
			insetPadding: '20 20 5 20',
			width: 800,
			height: 600,
			legend: {
				type: 'sprite'
			},
			axes: [ {
				type: 'numeric',
				position: 'left',
				fields: [ 'count' ],
				majorTickSteps: 10,
				reconcileRange: true,
				grid: true,
				minimum: 0
			}, {
				type: 'category',
				position: 'bottom',
				fields: 'complaint',
				label: {
					rotate: {
						degrees: -60
					}
				}
			}, {
				type: 'numeric',
				position: 'right',
				fields: [ 'cumnumber' ],
				reconcileRange: true,
				majorTickSteps: 10,
				renderer: 'onAxisLabelRender'
			} ],
			series: [ {
				type: 'bar',
				title: 'Causes',
				xField: 'complaint',
				yField: 'count',
				style: {
					opacity: 0.80
				},
				highlight: {
					fillStyle: 'rgba(204, 230, 73, 1.0)',
					strokeStyle: 'black'
				},
				tooltip: {
					trackMouse: true,
					renderer: 'onBarSeriesTooltipRender'
				}
			}, {
				type: 'line',
				title: 'Cumulative %',
				xField: 'complaint',
				yField: 'cumnumber',
				style: {
					lineWidth: 2,
					opacity: 0.80
				},
				marker: {
					type: 'cross',
					animation: {
						duration: 200
					}
				},
				highlight: {
					scaling: 2,
					rotationRads: Math.PI / 4
				},
				tooltip: {
					trackMouse: true,
					renderer: 'onLineSeriesTooltipRender'
				}
			} ]
		},  {
			xtype: 'toolbar',
			docked: 'top',

	    	defaults: {
	    		labelAlign: 'left'
	    	},
			items: [ {
				xtype: 'textfield',
				label: 'Filename',
				width: 200,
				labelWidth: 60,
				bind: '{filename}'
			}, {
				xtype: 'numberfield',
				label: 'Width',
				bind: '{width}',
				width: 100,
				labelWidth: 45
			}, {
				xtype: 'numberfield',
				label: 'Height',
				bind: '{height}',
				width: 100,
				labelWidth: 50
			},  {
				text: 'Download as PNG',
				handler: 'downloadPng'
			},  {
				xtype: 'slider',
				minValue: 0,
				maxValue: 100,
				labelAlign: 'top',
				bind: '{quality}',
				label: 'Quality',
				width: 200
			}, {
				xtype: 'displayfield',
				bind: 'Quality: {quality}',
				width: 100
			}, {
				text: 'Download as JPEG',
				handler: 'donwloadJpeg'
			},  {
				text: 'Download as GIF',
				handler: 'downloadGif'
			} ]
		}, {
			xtype: 'toolbar',
			docked: 'top',

	    	defaults: {
	    		labelAlign: 'left'
	    	},	        
			items: [ {
				xtype: 'fieldpanel',
				items: [ {
					 xtype: 'radiofield',
					 label: 'Portrait',
					name: 'orientation',
					value: 'portrait',
		        	viewModel: {
		    			formulas: {
		    				checked: {
		    					bind: '{pdf.orientation}',
		    					get: (value) => value === 'portrait',    				
		    					set: function(value) {
		    						this.set('pdf.orientation', value ? 'portrait' : 'landscape');
		    					}
		    				}
		    			}
		    		},
		    		bind: {
		    			checked: '{checked}'
		    		}					
				}, {
					 xtype: 'radiofield',
					 label: 'Landscape',
					name: 'orientation',
					value: 'landscape',
		        	viewModel: {
		    			formulas: {
		    				checked: {
		    					bind: '{pdf.orientation}',
		    					get: (value) => value === 'landscape',    				
		    					set: function(value) {
		    						this.set('pdf.orientation', value ? 'landscape' : 'portrait');
		    					}
		    				}
		    			}
		    		},
		    		bind: {
		    			checked: '{checked}'
		    		}			
				} ]
			}, {
				xtype: 'combobox',
				label: 'Format',		
				store: {
					fields: [ 'value', 'text' ],
					data: [ {
						value: 'A3',
						text: 'A3'
					}, {
						value: 'A4',
						text: 'A4'
					}, {
						value: 'A5',
						text: 'A5'
					}, {
						value: 'Legal',
						text: 'Legal'
					}, {
						value: 'Letter',
						text: 'Letter'
					}, {
						value: 'Tabloid',
						text: 'Tabloid'
					} ]
				},
				queryMode: 'local',
				bind: '{pdf.format}',
				width: 150,
				labelWidth: 50
			},  {
				xtype: 'numberfield',
				label: 'Width',
				bind: '{pdf.width}',
				width: 100,
				labelWidth: 45
			}, {
				xtype: 'numberfield',
				label: 'Height',
				bind: '{pdf.height}',
				width: 100,
				labelWidth: 50
			}, {
				xtype: 'numberfield',
				label: 'Border',
				bind: '{pdf.border}',
				width: 100,
				labelWidth: 50
			}, {
				xtype: 'combobox',
				label: 'Unit',
				store: {
					fields: [ 'value', 'text' ],
					data: [ {
						value: 'px',
						text: 'px'
					}, {
						value: 'mm',
						text: 'mm'
					}, {
						value: 'cm',
						text: 'cm'
					}, {
						value: 'in',
						text: 'in'
					} ]
				},				
				queryMode: 'local',
				bind: '{pdf.unit}',
				width: 150,
				labelWidth: 50
			},  {
				text: 'Download as PDF',
				handler: 'downloadPdf'
			} ]

		}  ]
	});
});
