libs folder (compile-only dependencies)

This project compiles directly against Gendustry and bdlib for Minecraft 1.12.2. Since they are not published on a public Maven with deobf artifacts, provide local deobf jars here.

Place files as:
- libs/gendustry-deobf.jar
- libs/bdlib-deobf.jar

Ways to obtain deobf jars:
- From your development workspace after importing Gendustry/bdlib into ForgeGradle; the deobf jars are typically in their build/libs.
- Use a deobfuscation tool (e.g., BON/ForgeFlower) on the official jars to generate deobf dev jars.
- If you already have a working dev environment that depends on these, copy the resolved deobf artifacts from Gradle caches.

Note: These are compileOnly and will NOT be bundled into your built jar. At runtime, the user must have Gendustry (and bdlib) installed, which Gendustry requires anyway.
