# MCProtocolLib
MCProtocolLib is a simple library for communicating with a Minecraft client/server. It aims to allow people to make custom bots, clients, or servers for Minecraft easily.

## ttRMS fork

Our fork will contain any necessary changes required for 2b2t.org. All our changes will be on the `ttrms` branch.

Huge thanks to these wonderful people/groups for getting us to where we are today:

- [@Steveice10](https://github.com/Steveice10)
- [@DaMatrix](https://github.com/DaMatrix)
- [@GeyserMC](https://github.com/GeyserMC)

## Adding as a Dependency

The recommended way of fetching MCProtocolLib is through [JitPack](https://jitpack.io/).

`build.gradle`:

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.ttrms:mcprotocollib:ttrms-SNAPSHOT'
}
```

## Example Code

See [example usage here](https://github.com/ttRMS/MCProtocolLib/blob/ttrms/example/com/github/steveice10/mc/protocol/test/MinecraftProtocolTest.java).

## Building the Source

MCProtocolLib uses Maven to manage dependencies. Simply run `mvn clean install` in the project root.

## License

MCProtocolLib is licensed under the **[MIT license](http://www.opensource.org/licenses/mit-license.html)**.
