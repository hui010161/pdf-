# 错题组卷器（Java 桌面程序）

从多个 PDF 里**手动框选**题目，**拖拽**到拼版页上排版，最后**导出成一份 PDF**。

界面用 Swing，PDF 解析/渲染/生成用 Apache PDFBox。依赖 jar 已经放在 `lib/` 里，
**不需要安装 Maven / Gradle**，只要机器上有 JDK。

---

## 一、怎么运行

1. 确认已装 JDK（命令行执行 `javac -version` 能看到版本号即可）。
2. 双击 **`运行.bat`**（首次会自动编译，之后直接启动，控制台窗口会自己关掉）。

> **关于 bat 文件和编码**（踩过两次坑，都是编码问题，与 Java 程序本身无关）
>
> 1. 两个 `.bat` 的内容**只用 ASCII 字符**，编码为 **ASCII + CRLF、无 BOM**，也**不使用 `chcp`**。
>    原因：cmd 用「控制台代码页」解析 `.bat` 文件，里面一旦有 UTF-8 中文（含中文注释、
>    甚至带中文的 `chcp 65001` 行），命令会被拆坏，报
>    `'p0"' 不是内部或外部命令`、`'紪璇?..' 不是内部或外部命令` 之类错误。
> 2. 编译用的**清单文件 `build\sources.txt` 由 JVM 自己生成**（`tools\GenList.java`，用
>    `java GenList.java …` 单文件源码模式运行，无需先编译），内容是**纯 ASCII 相对路径**
>    （如 `src/juanzi/App.java`）。
>    原因：如果让 `dir` 写清单，它会用控制台代码页（中文 Windows 是 GBK）写文件，
>    而 JDK 18+ 的 `javac` 默认按 **UTF-8** 读 `@清单`，于是报
>    `MalformedInputException: Input length = 2`（就是你遇到的那个错）。
>    让写和读都走同一个 JVM、且内容全 ASCII，这个坑就彻底没有了。
>
> 所以：不要往这两个 `.bat` 里加中文；改动后请保持纯 ASCII 保存。

如果想手动编译：

```bat
java  -Dfile.encoding=UTF-8 tools\GenList.java build\sources.txt src
javac -encoding UTF-8 -cp "lib\pdfbox-3.0.3.jar;lib\fontbox-3.0.3.jar;lib\pdfbox-io-3.0.3.jar;lib\commons-logging-1.3.4.jar" -d build @build\sources.txt
java  -Dfile.encoding=UTF-8 -cp "build;lib\pdfbox-3.0.3.jar;lib\fontbox-3.0.3.jar;lib\pdfbox-io-3.0.3.jar;lib\commons-logging-1.3.4.jar" juanzi.App
```

---

## 二、操作流程（就四步）

### 1. 导入 PDF
点工具栏 **「导入 PDF…」**，按住 Ctrl 或 Shift 可以一次选多个文件。
左侧「源文件」树里出现每个文档的每一页，点某一页，中间就会显示它的预览。

### 2. 框选内容
在中间的页面预览上**按住鼠标左键拖出一个矩形**，松开即完成框选。

- 导入后预览会**自动「适应窗口」**，整页都能看见——这点很关键：如果预览右边被窗口裁掉，
  框选时就会漏掉右侧的字，拼出来的 PDF 会出现文字被硬切。
- 框选出来的内容会出现在**右下角「框选内容」列表**里（带缩略图）。
- 拖拽过程中会实时显示该区域的尺寸（pt）；如果框选宽度超过页宽，状态栏会提示，放入时会自动缩小。
- 想更清晰/更小体积，改工具栏的 **「框选精度」**（150 / 200 / 300 / 400 DPI）。默认 300 DPI。

### 缩放与侧栏
- **源页缩放**：`−` / `+` 手动缩放；**「适应窗口」**整页可见；**「适应宽度」**只保证宽度完整，适合一行行细框。
- **拼版缩放**：`−` / `+` / **「适应窗口」**；也可按住 `Ctrl` 滚滚轮，或按 `Ctrl+0 / Ctrl+- / Ctrl+=`。
  觉得拼版页占屏幕太多就点 `−`（最小 10%）。
- **两侧边栏都能拖动**：三条分隔条都可以拖 —
  ① 源文件树 ↔ 源页预览、② 预览 ↔ 框选内容列表（上下）、③ 左半区 ↔ 拼版工作区。
  每条分隔条上还有一对小箭头，点一下可直接折叠/展开对应面板。
  拖动后的比例会记住，改变窗口大小时按比例跟随。
- **工具栏是两行**：第一行文件/编辑，第二行精度与缩放。这样窗口可以缩得很窄，
  不会因为一行按钮太长而卡住最小宽度。

### 3. 拖到拼版页
把右下角列表里的条目**直接拖到右边的拼版页**上，松手即放置（也可点「放入当前页」）。
拖动时画布上会出现一个虚线落点提示。

在拼版页上可以：

| 操作 | 方法 |
| --- | --- |
| 移动 | 直接拖动贴图（自动限制在页面内） |
| 缩放 | 拖动选中框的**四个角**（等比缩放） |
| 旋转 | 选中后点「旋转90°」或按 `R` |
| 置顶 | 选中后点「置顶」 |
| 删除 | 选中后点「删除贴图」或按 `Delete` |
| 缩放视图 | 工具栏「拼版缩放 +/−」「适应窗口」，或按住 `Ctrl` 滚滚轮 |

### 4. 导出
点 **「导出 PDF」**，选择保存位置即可。默认页面为 **A4 纵向 595×842 pt**，与常见试卷一致。

多页排版：拼版区**顶部有一排页码按钮**（1、2、3…），**点哪个数字就跳到哪一页**；
右侧一排功能按钮：**新建页 / 复制页 / 删除页 / 上移 / 下移 / 页面尺寸…**，
最右显示「共 N 页 · 当前第 M 页」。原先左侧的缩略图栏已经去掉，整块空间都留给拼版画布。
改页面尺寸时如果贴图超出新页面，会自动等比缩小并提示。

---

## 三、快捷键

| 快捷键 | 功能 |
| --- | --- |
| `Ctrl+Z` / `Ctrl+Y` | 撤销 / 重做 |
| `Delete` | 删除选中的贴图 |
| `R` | 选中贴图旋转 90° |
| `Ctrl+O` | 导入 PDF |
| `Ctrl+滚轮` | 缩放拼版视图 |
| `Ctrl+0` | 拼版页适应窗口 |
| `Ctrl+-` / `Ctrl+=` | 拼版视图缩小 / 放大 |

---

## 四、目录结构

```
错题组卷器/
├── 运行.bat              启动（自动编译）
├── 编译.bat              只编译
├── tools/
│   ├── GenList.java      生成 build\sources.txt（纯 ASCII 相对路径）
│   ├── PdfInfo.java      打印 PDF 页数/尺寸，可渲染成 PNG
│   └── ImgRects.java     打印每页图片对象实际占据的矩形（排查排版问题用）
├── lib/                  PDFBox 依赖（pdfbox / fontbox / pdfbox-io / commons-logging）
└── src/juanzi/
    ├── App.java                     入口
    ├── model/
    │   ├── SelectionItem.java       一个“框选内容块”（高分辨率图 + 点尺寸）
    │   ├── PlacedImage.java         拼版页上的一个贴图（位置/大小/旋转/绘制）
    │   ├── CompPage.java            拼版结果的一页（A4，595×842 pt）
    │   ├── ComposerStore.java       工程状态：内容库 + 页面 + 撤销重做
    │   └── ImageUtils.java          缩略图
    ├── pdf/
    │   ├── SourceDoc.java           源 PDF 的打开与按 DPI 渲染
    │   └── PdfExporter.java         导出成品 PDF
    ├── ui/
    │   ├── MainFrame.java           主窗口、工具栏、状态栏、事件
    │   ├── SourceListPanel.java     左边源文件树
    │   ├── PageViewPanel.java       源页预览 + 鼠标框选
    │   ├── SelectionListPanel.java  框选内容列表（拖拽源）
    │   ├── ComposeView.java         拼版画布（拖拽目标、移动缩放）
    │   ├── ComposePanel.java        拼版工作区（顶部页码条 + 功能按钮 + 画布）
    │   ├── ViewportUtil.java        取“真正能看见多少像素”（缩放计算用）
    │   └── PageRef.java             “某文档的第几页”
    └── tools/
        ├── SelfTest.java            无界面自检：模拟框选→拼版→导出
        └── GuiSmokeTest.java        界面冒烟测试：建窗体、跑一遍全流程
```

---

## 五、自检（可选）

验证整条链路是否正常（不会弹窗）：

```bat
java -Dfile.encoding=UTF-8 -cp "build;lib\pdfbox-3.0.3.jar;lib\fontbox-3.0.3.jar;lib\pdfbox-io-3.0.3.jar;lib\commons-logging-1.3.4.jar" juanzi.tools.SelfTest 某文件.pdf out\self-test.pdf
java -Dfile.encoding=UTF-8 -cp "build;lib\pdfbox-3.0.3.jar;lib\fontbox-3.0.3.jar;lib\pdfbox-io-3.0.3.jar;lib\commons-logging-1.3.4.jar" juanzi.tools.GuiSmokeTest 某文件.pdf
```

`SelfTest` 会输出成品 PDF 和首页预览图 `out\preview\check-1.png`，方便肉眼比对排版效果。

---

## 六、实现要点

- **缩放（重要）**：显示尺寸 = 页面点 × 缩放系数，而缩放系数必须按**滚动视口的可视尺寸**
  （`JViewport.getExtentSize()`，再扣掉滚动条）来算。用 `getParent().getWidth()` 是不行的：
  那是 `JSplitPane` 内部容器，尺寸和真正能看到的范围不一致，结果就是**页面撑出屏幕、还缩不小**。
  见 `ViewportUtil`；`PageViewPanel` / `ComposeView` 都监听视口 `extentSize` 变化自动重算。
- **保持原始物理尺寸**：框选时记录该区域在页面上的点尺寸（宽度/高度 pt），
  放进拼版页时按同样的点尺寸摆放，所以题目不会忽大忽小。
- **版心保护**：放入的贴图若比版心宽（页宽 − 20pt），会自动等比缩小；
  拖动时也被限制在页面内，避免内容跑到纸外被切掉。
- **清晰度**：框选内容按所选 DPI（默认 300）从原始 PDF 重新光栅化；
  导出时整页按 2 倍（144 dpi）渲染并**无损**写入 PDF，打印/放大都不糊。
- **裁剪的坑**：绘制贴图时裁剪区域必须先换算成设备坐标再 `setClip`，
  否则 Java2D 在缩放绘制位图时会走错分块路径，图片会被截断（已修复并有对照实验）。
- **撤销**：每次改动前对「页面 + 内容库」做一份轻量拷贝，最多保留 100 步。
- **分隔条**：三条 `JSplitPane` 分隔条都可拖动，并开启 `setOneTouchExpandable`（自带折叠箭头）；
  拖动比例被记录，窗口尺寸变化时按比例重新分配。
