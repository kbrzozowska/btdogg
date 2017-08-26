import DS from 'ember-data';
import Ember from 'ember';

export default DS.JSONAPISerializer.extend({
  normalizeResponse(store, primaryModelClass, payload, id, requestType) {
    if (Object.prototype.toString.call( payload.content ) === '[object Array]') {
      let field = Ember.String.pluralize(primaryModelClass.modelName);
      payload[field] = payload.content;
    } else {
      payload[primaryModelClass.modelName] = payload.content;
    }
    delete payload.content;

    return this._super(store, primaryModelClass, payload, id, requestType);
  },
  modelNameFromPayloadKey(key) {
    return this._super(key);
  }
});
