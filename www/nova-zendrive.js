var exec = require("cordova/exec");
var obj = {};

obj.setup = function (arg0, success, error) {
  exec(success, error, "NRCore", "setUp", [arg0]);
};

obj.onDriveStart = function (arg0, success, error) {
  exec(success, error, "NRCore", "onDriveStart", [arg0]);
};

obj.isSDKSetup = function (arg0, success, error) {
  exec(success, error, "NRCore", "isSDKSetup", [arg0]);
};

module.exports = obj;
