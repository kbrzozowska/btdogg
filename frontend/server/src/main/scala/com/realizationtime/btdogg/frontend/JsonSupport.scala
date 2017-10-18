package com.realizationtime.btdogg.frontend

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.{SerializationFeature, SerializerProvider}
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.realizationtime.btdogg.commons.TKey
import de.heikoseeberger.akkahttpjackson.JacksonSupport
import de.heikoseeberger.akkahttpjackson.JacksonSupport.defaultObjectMapper

trait JsonSupport extends JacksonSupport {

  defaultObjectMapper.registerModule(new JavaTimeModule)
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

  val tKeySerializer = new StdSerializer[TKey](classOf[TKey]) {
    override def serialize(value: TKey, gen: JsonGenerator, provider: SerializerProvider) = {
      gen.writeString(value.hash)
    }
  }

  val tKeyModule = new SimpleModule()
  tKeyModule.addSerializer(tKeySerializer)
  defaultObjectMapper.registerModule(tKeyModule)

}
