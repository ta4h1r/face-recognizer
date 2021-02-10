// Dependencies
let router = require('express').Router();
var controller = require('./controller');

// Default API response 
router.get('/', (req, res) => {
	console.log('Test GET hit');
	res.json({
		status: 'API working',
		message: 'Welcome to my world'
	});
});

// Table routes 
router.route('/push-data')
	.get(controller.index)
	.post(controller.handler);
	
// Export API routes 
module.exports = router;