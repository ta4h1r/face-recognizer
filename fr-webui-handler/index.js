// Dependencies
let express = require('express'); 			// Port handler
let apiRoutes = require('./api-routes'); 	// Response definitions 
let bodyParser = require('body-parser'); 	// For handling request data
let mongoose = require('mongoose');		 	// For Mongo DB interactions
const fs = require('fs')  				    // readFile function is defined
var cors = require('cors');                 // Enables cors policies on the resource for web interaction

// Definitions 
let app = express();
var port = process.env.PORT || 8005; 
const dbName = 'fr-module';

main();

async function main() {
	const pass = await getText();
	const uri = 'mongodb+srv://ta4h1r:' + pass + '@sandbox-cluster-0-2wusu.mongodb.net/' + dbName + '?retryWrites=true&w=majority';
	const options = {
		useUnifiedTopology: true,
		useNewUrlParser: true
	};

	// Connect to Mongo DB and set connection variable 
	mongoose.connect(uri, options);

	// Check for succesful DB connection
	var db = mongoose.connection;
	if(!db)
		console.log("Error connecting DB");
	else
		console.log("Connected to DB: " + dbName);

	// Configure bodyparser to handle post requests 
	app.use(bodyParser.urlencoded({
		extended: true
	}));

	app.use(bodyParser.json());
	
	// Set cors policy 
	app.use(cors());
	
	// Send default message for root path 
	app.get('/', (req, res) => res.send('Nothing to do here.'));

	// Configure router for /api path
	app.use('/api', apiRoutes);

	// Start listening on the server
	app.listen(port, () => {
		console.log('Listening on port ' + port);
	});

}

function getText() {
	return new Promise((resolve, reject) => {
		fs.readFile('pass.txt', 'utf-8', async (err, data) => { 
			if (err) throw err; 
			resolve(data);
		});
	}) 
} 
