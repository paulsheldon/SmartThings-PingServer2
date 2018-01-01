const ping = require('ping');
const http = require('http');
const url = require('url');
const httprequest = require('request');

const httpPort = 4001;

var pingDevicesTimer = null;
var pingDevicesInterval = 3;
var sockets = {};
sockets.service = "pingserver2";
sockets.devices = [];
var oauthToken = null;
var oauthURL = null;
var timeOut = 600; //15 minutes

const requestHandler = (request, response) => {
	console.log("[requestHandler] -----------> Started.");
    response.writeHead(200, {'Content-Type': 'application/json'});

    let q = url.parse(request.url, true).query;
	let p = url.parse(request.url, true).pathname.replace(/^\/|\/$/g, '');
	console.log("[requestHandler]     p: " + p);
	console.log("[requestHandler]     q: " + JSON.stringify(q));
	console.log('[requestHandler]     pathname: ' + url.parse(request.url, true).pathname);
	console.log('[requestHandler]     url: ' + request.url);

	if (p == 'ping') {
		var ret = {};
		ret.service = sockets.service;
		if (pingDevicesTimer != null && oauthToken != null && oauthURL != null) { // If the ring object/smartthings tokens were initialized
			console.log("[requestHandler] Ping -> ok.");
			ret.status = "ok";
		} else {
			console.log("[requestHandler] Ping -> Requesting init.");
			ret.status = "error";
			ret.message = "init";
		};
		response.end(JSON.stringify(ret));
	} else if (p == 'init') {
		if (q.token != null && q.url != null) {
			console.log("[requestHandler] Initializing PingServer2.");
			console.log('[requestHandler]     q.token: ' + q.token);	// SmartThings token.
			console.log('[requestHandler]     q.url: ' + q.url);		// SmartThings callback url.
			console.log('[requestHandler]     q.dl: ' + q.dl);		// SmartThings device list.
			console.log('[requestHandler]     q.to: ' + q.to);
			console.log('[requestHandler]     q.pdi: ' + q.pdi);

			oauthToken = q.token;
			oauthURL = q.url;
			if (q.to != null) { timeOut = q.to };
			if (q.pdi != null) { pingDevicesInterval = q.pdi };

			sockets.devices = [];
			q.dl.split(',').forEach ( function(id) {
				var d = {};
				d.id = id;
				d.status = null;
				d.lastonline = null;
				sockets.devices.push(d);
			});

			sockets.status = 'ok';
			sockets.message = '';
			response.end(JSON.stringify(sockets));

			startDevicesPing(sockets.devices, oauthToken, oauthURL, timeOut);
		} else {
			sockets.status = "error";
			sockets.message = "init"
			response.end(JSON.stringify(sockets));
		};
	} else {
		sockets.status = "error";
		sockets.message = "init"
		response.end(JSON.stringify(sockets));
	};
};

var sendRequest = function (id, attribute, state) {
	// attribute = [presence]
	// command = [present/not present]
	var deviceURL = oauthURL + '/device/' + id + '/' + attribute + '/' + state + '/?access_token=' + oauthToken;
	console.log('[sendRequest] deviceURL: ' + deviceURL);
	httprequest(deviceURL, {json: true}, (err,res,body) => {});
};

function startDevicesPing(devices, token, baseurl, timeOut) {
	const pingDevice = (device, token, baseurl, timeOut) => {
		//console.log('[pingDevice] device: ' + JSON.stringify(device));
		ping.sys.probe(device.id, function(isAlive) {
			var newStatus = "not%20present";

			if (isAlive) { newStatus = "present" };

			if (newStatus == "present") { device.lastonline = Date.now(); };

			if (device.status == null) {
				device.status = newStatus;
				console.log('[pingDevice] Setting initial status.');
				console.log('[pingDevice]     device.id: ' + device.id);
				console.log('[pingDevice]     device.status: ' + device.status);
				console.log('[pingDevice]     device.lastonline: ' + device.lastonline);
				sendRequest(device.id, "presence", device.status);
			} else if (device.status != newStatus) {
				if (device.status == "present") { // device is going from online to offline
					if (((Date.now() - device.lastonline) / 1000) > timeOut) {
						console.log('[pingDevice] ' + device.id + ' has been offline for more than ' + timeOut + ' seconds.');
						device.status = newStatus;
						sendRequest(device.id, "presence", device.status);
					} else {
						console.log('[startDevicePing] ' + device.id + ' has not been offline long enough.');
					};
				} else { // device is going from offline to online
					device.status = newStatus;
					sendRequest(device.id, "presence", device.status);
				};
			};
		});
	};
	const pingDevices = (devices, token, baseurl, timeOut) => {
		devices.forEach(function(device) {
			pingDevice(device, token, baseurl, timeOut);
		});
	};

	if (pingDevicesTimer != null) {
		clearInterval(pingDevicesTimer);
		pingDevicesTimer = null;
	};
	if (token != null && baseurl != null && devices != null) {
		pingDevicesTimer = setInterval(pingDevices, pingDevicesInterval * 1000,  devices, token, baseurl, timeOut);
		console.log('[startDevicePing] pingDevicesTimer started.');
	};
};

const httpServer = http.createServer(requestHandler);

httpServer.listen(httpPort, (err) => {
    if (err) {
        return console.log('something bad happened', err)
    }
    console.log(`http server is listening on ${httpPort}`)
});
