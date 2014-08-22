
Ext.onReady(function() {

    var myDataStore = new Ext.data.JsonStore({
        fields: ['complaint', 'count', 'cumnumber', 'cumpercent' ],
        data: [
            { complaint: 'Overpriced', count: 543, cumnumber: 543, cumpercent: 31 },
            { complaint: 'Small Portions', count: 412, cumnumber: 955, cumpercent: 55 },
            { complaint: 'High Wait Time', count: 245, cumnumber: 1200, cumpercent: 69 },
            { complaint: 'Tasteless Food', count: 187, cumnumber: 1387, cumpercent: 80 },
            { complaint: 'Bad Ambiance', count: 134, cumnumber: 1521, cumpercent: 88 },
            { complaint: 'Not Clean', count: 98, cumnumber: 1619, cumpercent: 93 },
            { complaint: 'Too Noisy', count: 65, cumnumber: 1684, cumpercent: 97 },
            { complaint: 'Salty Food', count: 41, cumnumber: 1725, cumpercent: 99 },
            { complaint: 'Unfriendly Staff', count: 12, cumnumber: 1737, cumpercent: 100 }
        ]
    });
	
	new Ext.panel.Panel({
		plugins: 'viewport',
		layout: 'fit',
	    title: 'Download Chart Example',
	    viewModel: {
	    	
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
	      } else if (format === 'pdf' && pdf) {
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
	    		  
	    		  delete pdf.unit;
	    	  }
	    	  
	    	  if (pdf.orientation) {
	    		  pdf.orientation = pdf.orientation.orientation;
	    	  }

	    	  config.pdf = pdf;
	      }
	      
	      console.log(config);
	      
		  this.down('cartesian').download(config);
	    },
	    
		dockedItems: [ {
			xtype: 'toolbar',
			items: [ {
				xtype: 'textfield',
				fieldLabel: 'Filename',
				labelWidth: 60,
				width: 160,
				bind: '{filename}'
			},
			{
				xtype: 'numberfield',
				fieldLabel: 'Width',
				labelWidth: 40,
				width: 110,
				bind: '{width}'
			},
			{
				xtype: 'numberfield',
				fieldLabel: 'Height',
				labelWidth: 40,
				width: 110,
				bind: '{height}'
			},			
			'-',
			{
				text: 'Download as PNG',
				handler: function(btn) {
					btn.up('panel').downloadChart('png');
				}
			}, 
			'-', 
			{
				xtype: 'slider',
				minValue: 0,
				maxValue: 100,
				bind: '{quality}',
				fieldLabel: 'Quality',
				labelWidth: 50,
				publishOnComplete: false,
				width: 200,
				value: 100
			}, {
				xtype: 'displayfield',
				bind: '{quality}',
				width: 20
			},			
			{
				text: 'Download as JPEG',
				handler: function(btn) {
			      btn.up('panel').downloadChart('jpeg');
				}
			}, '-', {
				text: 'Download as GIF',
				handler: function(btn) {
					btn.up('panel').downloadChart('gif');
				}
			} ]
		}, {
			
			xtype: 'toolbar',
			items: [ 
				{				
					xtype: 'radiogroup',
			        fieldLabel: 'Orientation',
			        columns: 2,
			        labelWidth: 60,
			        vertical: false,
			        width: 230,
			        items: [
			            { boxLabel: 'Portrait', name: 'orientation', inputValue: 'portrait'},
			            { boxLabel: 'Landscape', name: 'orientation', inputValue: 'landscape'}
			        ],
			        bind: {
			        	value: '{pdf.orientation}'
			        }
				},'-', {
					xtype: 'combobox',
					fieldLabel: 'Format',
					labelWidth: 50,
				    store: ['A3', 'A4', 'A5', 'Legal', 'Letter', 'Tabloid'],				    
				    width: 150,
				    queryMode: 'local',
				    bind: '{pdf.format}'
				},'-', {
					xtype: 'numberfield',
					fieldLabel: 'Width',
					labelWidth: 40,
					width: 100,
					bind: '{pdf.width}'
				}, {
					xtype: 'numberfield',
					fieldLabel: 'Height',
					labelWidth: 40,
					width: 100,
					bind: '{pdf.height}'
				}, {
					xtype: 'numberfield',
					fieldLabel: 'Border',
					labelWidth: 40,
					width: 100,
					bind: '{pdf.border}'
				}, {
					xtype: 'combobox',
					fieldLabel: 'Unit',
					labelWidth: 30,
				    store: ['px', 'mm', 'cm', 'in'],				    
				    width: 100,
				    queryMode: 'local',
				    bind: '{pdf.unit}'
				},'-', {
					text: 'Download as PDF',
					handler: function(btn) {
						btn.up('panel').downloadChart('pdf');						
					}
				}				
			]
			
		}],
	    
	    items: [{
            xtype: 'cartesian',
            margin: '30 0 0 0',
            defaultDownloadServerUrl: 'downloadchart',
            
            width: '100%',
            height: 500,
            store: myDataStore,
            insetPadding: '40 40 20 40',
            legend: {
                docked: 'bottom'
            },
            sprites: [{
                type: 'text',
                text: 'Restaurant Complaints by Reported Cause',
                fontSize: 22,
                width: 100,
                height: 30,
                x: 40,
                y: 20
            }, {
                type: 'text',
                text: 'Data: Restaurant Complaints',
                font: '10px Helvetica',
                x: 12,
                y: 650
            }],
            axes: [{
                type: 'numeric',
                position: 'left',
                fields: ['count'],
                majorTickSteps: 10,
                reconcileRange: true,
                grid: true,
                minimum: 0,
                maximum: 1800
            }, {
                type: 'category',
                position: 'bottom',
                fields: 'complaint',
                label: {
                    rotate: {
                        degrees: -45
                    }
                }
            }, {
                type: 'numeric',
                position: 'right',
                fields: ['cumnumber'],
                reconcileRange: true,
                majorTickSteps: 10,
                renderer: function (v) {
                    var total = this.getAxis().getRange()[1];
                    return (v / total * 100).toFixed(0) + '%';
                }
            }],
            series: [{
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
                    style: 'background: #fff',
                    renderer: function(record, item) {
                        this.setHtml(record.get('complaint') + ': ' + record.get('count') + ' responses.');
                    }
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
                    fx: {
                        duration: 200
                    }
                },
                highlightCfg: {
                    scaling: 2,
                    rotationRads: Math.PI / 4
                },
                tooltip: {
                    trackMouse: true,
                    style: 'background: #fff',
                    renderer: function(record, item) {
                        var store = record.store,
                            i, complaints = [];
                        for (i = 0; i <= item.index; i++) {
                            complaints.push(store.getAt(i).get('complaint'));
                        }
                        this.setHtml('<div style="text-align: center; font-weight: bold">' + record.get('cumpercent') + '%</div>' + complaints.join('<br>'));
                    }
                }
            }]
        }]	    
	});
});
