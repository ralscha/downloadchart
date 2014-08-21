
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
	    title: 'Download Charts Example',
	    viewModel: {
	    	
	    },
	    
		dockedItems: [ {
			xtype: 'toolbar',
			items: [ {
				xtype: 'textfield',
				fieldLabel: 'Filename',
				labelWidth: 60,
				width: 150,
				bind: '{filename}'
			},
			{
				xtype: 'numberfield',
				fieldLabel: 'Width',
				labelWidth: 40,
				width: 100,
				bind: '{width}'
			},
			{
				xtype: 'numberfield',
				fieldLabel: 'Height',
				labelWidth: 40,
				width: 100,
				bind: '{height}'
			},			
			'-',
			{
				text: 'Download as PNG',
				handler: function(btn) {
					btn.up('panel').down('cartesian').download();
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
				width: 200
			}, {
				xtype: 'displayfield',
				bind: '{quality}',
				width: 20
			},			
			{
				text: 'Download as JPEG',
				handler: function(btn) {
			      var config = {
				    format: 'jpeg',
				  };
			      var vm = btn.up('panel').getViewModel();
				  
			      var filename = vm.get('filename');
			      if (filename) {
			    	  config.filename = filename;
			      }
			      
			      var quality = vm.get('quality');
				  if (quality) {
				    config.jpeg = {
					  quality: quality
					}
				  }
			      
				  btn.up('panel').down('cartesian').download(config);
				}
			}, '-', {
				text: 'Download as GIF',
				handler: function(btn) {
					btn.up('panel').down('cartesian').download({
						format: 'gif'
					});
				}
			}, {
				text: 'Download as PDF (portrait)',
				handler: function(btn) {
					btn.up('panel').down('cartesian').download({
						format: 'pdf',
						pdf: {
							format: 'A4',
							orientation: 'portrait',
							border: '1cm'
						}
					});
				}
			}, {
				text: 'Download as PDF (landscape)',
				handler: function(btn) {
					btn.up('panel').down('cartesian').download({
						format: 'pdf',
						/*width: 400,
						height: 200,*/
						pdf: {
							format: 'A4',
							orientation: 'landscape',
							border: '5mm'
						}
					});
				}
			} ]
		} ],
	    
	    items: [{
            xtype: 'cartesian',
            theme: 'category2',
            
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
                x: 40, // the sprite x position
                y: 20  // the sprite y position
            }, {
                type: 'text',
                text: 'Data: Restaurant Complaints',
                font: '10px Helvetica',
                x: 12,
                y: 480
            }],
            axes: [{
                type: 'numeric',
                position: 'left',
                fields: ['count'],
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
