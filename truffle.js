require('dotenv').config();
require('babel-register');
require('babel-polyfill');

module.exports = {
  // See <http://truffleframework.com/docs/advanced/configuration>
  // for more about customizing your Truffle configuration!
  networks: {
    development: {
      host: "127.0.0.1",
      port: 7545,
      network_id: "*" // Match any network id
    }
  },
  networks: {
    mainnet: {
      host: "*.*.*.*",
      port: 7545,
      network_id: "*" // Match any network id
    }
  },  
  solc: {
    optimizer: {
      enabled: true,
      runs: 200
    }
  }
};
