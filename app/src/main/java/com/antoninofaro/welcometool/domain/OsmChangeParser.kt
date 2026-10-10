package com.antoninofaro.welcometool.domain

import com.antoninofaro.welcometool.data.model.ChangedElement
import com.antoninofaro.welcometool.data.model.ChangedElementGroup
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

object OsmChangeParser {
    fun parse(xmlString: String): ChangedElementGroup {
        val creates = mutableListOf<ChangedElement>()
        val modifies = mutableListOf<ChangedElement>()
        val deletes = mutableListOf<ChangedElement>()

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlString))

            var eventType = parser.eventType
            var currentAction: String? = null
            var currentElementId: Long? = null
            var currentElementType: String? = null
            var currentTags = mutableMapOf<String, String>()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name

                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tagName) {
                            "create", "modify", "delete" -> {
                                currentAction = tagName
                            }
                            "node", "way", "relation" -> {
                                currentElementType = tagName
                                currentElementId = parser.getAttributeValue(null, "id")?.toLongOrNull()
                                currentTags = mutableMapOf()
                            }
                            "tag" -> {
                                val k = parser.getAttributeValue(null, "k")
                                val v = parser.getAttributeValue(null, "v")
                                if (k != null && v != null) {
                                    currentTags[k] = v
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (tagName) {
                            "create", "modify", "delete" -> {
                                currentAction = null
                            }
                            "node", "way", "relation" -> {
                                if (currentAction != null && currentElementId != null && currentElementType != null) {
                                    val element = ChangedElement(
                                        type = currentElementType,
                                        id = currentElementId,
                                        action = currentAction,
                                        tags = currentTags.toMap()
                                    )
                                    when (currentAction) {
                                        "create" -> creates.add(element)
                                        "modify" -> modifies.add(element)
                                        "delete" -> deletes.add(element)
                                    }
                                }
                                currentElementId = null
                                currentElementType = null
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return ChangedElementGroup(creates, modifies, deletes)
    }
}
