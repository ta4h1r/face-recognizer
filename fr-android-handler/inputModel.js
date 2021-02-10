var mongoose = require('mongoose');

const collectionName = 'fr_face';

// Setup schema
var inputSchema = mongoose.Schema({
    id: {
        type: String,
        required: true
    },
    Name: {
        type: String,
        required: true
    },
    FaceID: {
        type: Array,
        required: true
	}
});

// Export Input model
var Input = module.exports = mongoose.model(collectionName, inputSchema);

module.exports.get = function (callback, limit) {
    Input.find(callback).limit(limit);
}
