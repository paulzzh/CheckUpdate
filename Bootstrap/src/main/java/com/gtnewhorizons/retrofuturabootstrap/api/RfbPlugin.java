package com.gtnewhorizons.retrofuturabootstrap.api;

/**
 * A runtime RFB plugin that can define a set of class transformers to transform any classes loaded through RFB.
 * Any jar that wants to register a RFB plugin during early load time must have a META-INF/rfb-plugin/my-plugin-id.properties file
 * declaring the plugin class and dependency information.
 * The runtime classpath is scanned, in addition to files with the .jar extension in the mods folder in the game directory.
 * The plugin class implementing this interface must have a public no-arguments constructor.
 */
public interface RfbPlugin {

}
