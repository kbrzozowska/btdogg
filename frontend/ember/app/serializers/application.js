import DS from 'ember-data';
import Ember from 'ember';

export default DS.JSONAPISerializer.extend({
  copyId(entity) {
    if (entity._id !== undefined)
      entity.id = entity._id;
  },
  normalizeResponse(store, primaryModelClass, payload, id, requestType) {
    if (Object.prototype.toString.call( payload.content ) === '[object Array]') {
      payload.content.forEach(this.copyId);
      let field = Ember.String.pluralize(primaryModelClass.modelName);
      payload[field] = payload.content;
      if (payload.content.length !== 1) {
        payload.data = payload.content;
      } else {
        payload[primaryModelClass.modelName] = payload.content[0];
        payload.data = payload.content[0];
      }
    } else {
      this.copyId(payload.content);
      payload[primaryModelClass.modelName] = payload.content;
    }
    delete payload.content;

    return this._super(store, primaryModelClass, payload, id, requestType);
  },
  modelNameFromPayloadKey(key) {
    return this._super(key);
  }
});
