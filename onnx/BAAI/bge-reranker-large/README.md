# BGE-Reranker-large 模型下载说明

## 模型文件

本目录需要以下文件才能正常运行 Reranker：

1. **model.onnx** - ONNX 模型文件（约 1.3GB）
2. **tokenizer.json** - Tokenizer 配置文件
3. **config.json** - 模型配置文件

## 下载方式

### 方式一：使用 HuggingFace CLI（推荐）

如果您有网络访问 HuggingFace 的条件：

```bash
# 安装 huggingface-cli
pip install huggingface_hub

# 下载模型
huggingface-cli download BAAI/bge-reranker-large --local-dir . --local-dir-use-symlinks False

# 或者下载 ONNX 版本
huggingface-cli download Xenova/bge-reranker-large --local-dir . --local-dir-use-symlinks False
```

### 方式二：使用 Python Transformers

```python
from transformers import AutoTokenizer, AutoModel

# 下载 tokenizer
tokenizer = AutoTokenizer.from_pretrained("BAAI/bge-reranker-large")
tokenizer.save_pretrained(".")

# 下载模型配置
model = AutoModel.from_pretrained("BAAI/bge-reranker-large", trust_remote_code=True)
model.save_pretrained(".")
```

### 方式三：手动下载

从以下链接手动下载文件：

1. https://huggingface.co/BAAI/bge-reranker-large
2. 下载以下文件到本目录：
   - `tokenizer.json`
   - `config.json`
   - `tokenizer_config.json`

### 方式四：使用国内镜像

如果在中国大陆，可以尝试：

```bash
# 使用 ModelScope
pip install modelscope

from modelscope import snapshot_download
model_dir = snapshot_download('AI-ModelScope/bge-reranker-large', cache_dir='.')
```

## 转换为 ONNX 格式

如果需要将 PyTorch 模型转换为 ONNX：

```python
import torch
from transformers import AutoModel, AutoTokenizer

# 加载模型和tokenizer
model = AutoModel.from_pretrained("BAAI/bge-reranker-large", trust_remote_code=True)
tokenizer = AutoTokenizer.from_pretrained("BAAI/bge-reranker-large")

# 准备示例输入
text = "query [SEP] document"
inputs = tokenizer(text, return_tensors="pt", padding=True, truncation=True, max_length=512)

# 导出到 ONNX
torch.onnx.export(
    model,
    (inputs["input_ids"], inputs["attention_mask"], inputs.get("token_type_ids", None)),
    "model.onnx",
    input_names=["input_ids", "attention_mask", "token_type_ids"],
    output_names=["logits"],
    dynamic_axes={
        "input_ids": {0: "batch_size", 1: "sequence_length"},
        "attention_mask": {0: "batch_size", 1: "sequence_length"},
        "token_type_ids": {0: "batch_size", 1: "sequence_length"},
        "logits": {0: "batch_size"}
    },
    opset_version=14
)
```

## 验证模型文件

下载完成后，验证文件是否存在：

```bash
ls -lh
# 应该看到：
# model.onnx (约 1.3GB)
# tokenizer.json
# config.json
```

## 配置文件

### config.json 基本内容

```json
{
  "architectures": ["BertModel"],
  "attention_probs_dropout_prob": 0.1,
  "hidden_act": "gelu",
  "hidden_dropout_prob": 0.1,
  "hidden_size": 1024,
  "initializer_range": 0.02,
  "intermediate_size": 4096,
  "layer_norm_eps": 1e-12,
  "max_position_embeddings": 512,
  "model_type": "bert",
  "num_attention_heads": 16,
  "num_hidden_layers": 24,
  "pad_token_id": 0,
  "type_vocab_size": 2,
  "vocab_size": 30522
}
```

### tokenizer.json

需要从 HuggingFace 下载完整的 tokenizer.json 文件，因为它包含了完整的词表和配置信息。

## 获取帮助

如果下载遇到问题，可以：
1. 检查网络连接
2. 使用代理或 VPN
3. 联系团队获取预下载的模型文件
