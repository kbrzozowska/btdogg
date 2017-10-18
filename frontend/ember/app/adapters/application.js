import DS from 'ember-data';
import ENV from 'btdogg/config/environment';
import Ember from 'ember';

export default DS.JSONAPIAdapter.extend({
  namespace: ENV.APP.apiNamespace,
  host: ENV.APP.apiHost,
  headers: {
    "Accept": "application/json"
  },
  pathForType: function(type) {
    let camelized = Ember.String.camelize(type);
    return Ember.String.singularize(camelized);
  }
});
