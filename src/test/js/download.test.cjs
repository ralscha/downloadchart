const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');

const publicPath = path.resolve(__dirname, '../../main/resources/public');

function downloadHarness(failSubmit = false) {
	let override;
	const forms = [];
	const body = {
		appendChild(form) { forms.push(form); }
	};
	const document = {
		body,
		createElement(tag) {
			return {
				tag, style: {}, children: [],
				appendChild(child) { this.children.push(child); },
				submit() {
					this.submitted = true;
					if (failSubmit) throw new Error('submission failed');
				},
				remove() { this.removed = true; }
			};
		}
	};
	const Ext = {
		define(name, config) { override = config; },
		apply: Object.assign,
		isObject: value => value !== null && typeof value === 'object',
		JSON: { encode: JSON.stringify }
	};
	vm.runInNewContext(fs.readFileSync(path.join(publicPath, 'DrawContainerOverride.js'), 'utf8'),
		{ Ext, document });
	const chart = {
		getImage: () => ({ data: 'data:image/png;base64,AAAA' }),
		defaultDownloadServerUrl: 'downloadchart',
		supportedOptions: Object.fromEntries(
			['version', 'data', 'filename', 'pdf', 'jpeg', 'scale'].map(name => [name, () => true]))
	};
	return { download: config => override.download.call(chart, config), forms, chart };
}

test('posts literal filenames and JSON without creating HTML or double encoding', () => {
	const harness = downloadHarness();
	const filename = 'Grüezi"><img src=x onerror=alert(1)>&';
	harness.download({ filename, pdf: { orientation: 'landscape', border: '0mm' }, scale: 2 });
	const form = harness.forms[0];
	assert.equal(form.action, 'downloadchart');
	assert.equal(form.method, 'POST');
	assert.equal(form.acceptCharset, 'UTF-8');
	assert.equal(form.style.display, 'none');
	const inputs = Object.fromEntries(form.children.map(input => [input.name, input.value]));
	assert.equal(inputs.filename, filename);
	assert.deepEqual(JSON.parse(inputs.pdf), { orientation: 'landscape', border: '0mm' });
	assert.equal(inputs.scale, 2);
	assert.equal(form.submitted, true);
	assert.equal(form.removed, true);
});

test('accepts null-prototype options and excludes unsupported or inherited fields', () => {
	const harness = downloadHarness();
	const config = Object.assign(Object.create(null), {
		filename: 'chart', url: '/custom', unsupported: 'no', hasOwnProperty: 'not a function'
	});
	harness.download(config);
	const form = harness.forms[0];
	assert.equal(form.action, '/custom');
	assert.deepEqual(form.children.map(input => input.name).sort(), ['data', 'filename', 'version']);
});

test('removes the exact form even when submission fails', () => {
	const harness = downloadHarness(true);
	assert.throws(() => harness.download(), /submission failed/);
	assert.equal(harness.forms[0].removed, true);
});

function demoHarness(values) {
	let definition, downloaded;
	const Ext = {
		onReady: fn => fn(),
		data: { JsonStore: function() {} },
		Container: function(config) { definition = config; },
		getBody: () => ({}),
		clone: structuredClone
	};
	vm.runInNewContext(fs.readFileSync(path.join(publicPath, 'app.js'), 'utf8'), { Ext });
	const controller = Object.assign({}, definition.controller, {
		getViewModel: () => ({ get: name => values[name] }),
		lookup: () => ({ download: config => { downloaded = config; } })
	});
	return { controller, definition, downloaded: () => downloaded };
}

test('demo passes quality zero and scale to the exporter', () => {
	const harness = demoHarness({ quality: 0, scale: 2 });
	harness.controller.downloadChart('jpeg');
	assert.equal(harness.downloaded().jpeg.quality, 0);
	assert.equal(harness.downloaded().scale, 2);
});

test('demo preserves landscape, serializes zero border and omits blank dimensions', () => {
	const pdf = { orientation: 'landscape', border: 0, width: '', height: null, unit: 'mm', format: 'A4' };
	const harness = demoHarness({ pdf });
	harness.controller.downloadChart('pdf');
	assert.deepEqual(JSON.parse(JSON.stringify(harness.downloaded().pdf)),
		{ orientation: 'landscape', border: '0mm', format: 'A4' });
	assert.equal(pdf.border, 0);
	assert.equal(pdf.unit, 'mm');
});

test('demo has valid PDF and JPEG defaults', () => {
	const harness = demoHarness({});
	assert.equal(harness.definition.viewModel.data.quality, 100);
	assert.equal(harness.definition.viewModel.data.pdf.orientation, 'portrait');
	assert.equal(harness.definition.viewModel.data.pdf.unit, 'mm');
});
