# ClientEvents.paintScreen

## Basic info

- Valid script types: [CLIENT]

- Has result? ✘

- Event class: [PaintScreenEventJS](https://github.com/KubeJS-Mods/KubeJS/tree/2001/common/src/main/java/dev/latvian/mods/kubejs/client/painter/screen/PaintScreenEventJS.java)

### Available fields:

| Name | Type | Static? |
| ---- | ---- | ------- |
| painter | Painter | ✘ |
| mouseX | int | ✘ |
| mouseY | int | ✘ |
| width | int | ✘ |
| height | int | ✘ |
| inventory | boolean | ✘ |
| mc | MinecraftClient | ✘ |
| font | TextRenderer | ✘ |
| graphics | DrawContext | ✘ |
| matrices | MatrixStack | ✘ |
| tesselator | Tessellator | ✘ |
| buffer | BufferBuilder | ✘ |
| delta | float | ✘ |
| screen | Screen | ✘ |

Note: Even if no fields are listed above, some methods are still available as fields through *beans*.

### Available methods:

| Name | Parameters | Return type | Static? |
| ---- | ---------- | ----------- | ------- |
| scale | float, float |  | void | ✘ |
| scale | float |  | void | ✘ |
| text | Text, int, int, int, boolean |  | void | ✘ |
| rectangle | float, float, float, float, float, int |  | void | ✘ |
| rectangle | float, float, float, float, float, int, float, float, float, float |  | void | ✘ |
| translate | double, double |  | void | ✘ |
| getVariables |  |  | VariableSet | ✘ |
| alignX | float, float, AlignMode |  | float | ✘ |
| rotateRad | float |  | void | ✘ |
| alignY | float, float, AlignMode |  | float | ✘ |
| rotateDeg | float |  | void | ✘ |
| rawText | OrderedText, int, int, int, boolean |  | void | ✘ |
| blend | boolean |  | void | ✘ |
| begin | DrawMode, VertexFormat |  | void | ✘ |
| end |  |  | void | ✘ |
| scale | float, float, float |  | void | ✘ |
| multiply | Quaternionf |  | void | ✘ |
| push |  |  | void | ✘ |
| pop |  |  | void | ✘ |
| setShaderInstance | Supplier<ShaderProgram> |  | void | ✘ |
| multiplyWithMatrix | Matrix4f |  | void | ✘ |
| resetShaderColor |  |  | void | ✘ |
| vertex | Matrix4f, float, float, float, int, float, float |  | void | ✘ |
| vertex | Matrix4f, float, float, float, int |  | void | ✘ |
| beginQuads | boolean |  | void | ✘ |
| beginQuads | VertexFormat |  | void | ✘ |
| setPositionColorShader |  |  | void | ✘ |
| setShaderColor | float, float, float, float |  | void | ✘ |
| setPositionColorTextureShader |  |  | void | ✘ |
| getMatrix |  |  | Matrix4f | ✘ |
| translate | double, double, double |  | void | ✘ |
| setShaderTexture | Identifier |  | void | ✘ |
| bindTextureForSetup | Identifier |  | void | ✘ |
| getEntity |  |  | Entity | ✘ |
| getPlayer |  |  | ClientPlayerEntity | ✘ |
| hasGameStage | String |  | boolean | ✘ |
| addGameStage | String |  | void | ✘ |
| removeGameStage | String |  | void | ✘ |
| getLevel |  |  | World | ✘ |
| getServer |  |  | MinecraftServer | ✘ |
| exit | Object |  | Object | ✘ |
| exit |  |  | Object | ✘ |
| cancel | Object |  | Object | ✘ |
| cancel |  |  | Object | ✘ |
| success | Object |  | Object | ✘ |
| success |  |  | Object | ✘ |


### Documented members:

- `boolean hasGameStage(String var0)`

  Parameters:
  - var0: String

```
Checks if the player has the specified game stage
```

- `void addGameStage(String var0)`

  Parameters:
  - var0: String

```
Adds the specified game stage to the player
```

- `void removeGameStage(String var0)`

  Parameters:
  - var0: String

```
Removes the specified game stage from the player
```

- `Object exit(Object var0)`

  Parameters:
  - var0: Object

```
Stops the event with the given exit value. Execution will be stopped **immediately**.

`exit` denotes a `default` outcome.
```

- `Object exit()`
```
Stops the event with default exit value. Execution will be stopped **immediately**.

`exit` denotes a `default` outcome.
```

- `Object cancel(Object var0)`

  Parameters:
  - var0: Object

```
Cancels the event with the given exit value. Execution will be stopped **immediately**.

`cancel` denotes a `false` outcome.
```

- `Object cancel()`
```
Cancels the event with default exit value. Execution will be stopped **immediately**.

`cancel` denotes a `false` outcome.
```

- `Object success(Object var0)`

  Parameters:
  - var0: Object

```
Stops the event with the given exit value. Execution will be stopped **immediately**.

`success` denotes a `true` outcome.
```

- `Object success()`
```
Stops the event with default exit value. Execution will be stopped **immediately**.

`success` denotes a `true` outcome.
```



### Example script:

```js
ClientEvents.paintScreen((event) => {
	// This space (un)intentionally left blank
});
```

