import DS from 'ember-data';

export default DS.Model.extend({
  files: DS.hasMany('torrent-file', {key: 'data', embedded: 'always'})
});
