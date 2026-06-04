# MLX 本地模型常规操作手册

> 适用范围：Apple Silicon macOS 本地运行 MLX / MLX-LM 模型，用于本项目知识抽取、问答生成、模型速度验证等场景。
>
> 当前已验证模型：Chat/OpenIE 使用 `mlx-community/Qwen3.5-2B-4bit`；Embedding 使用 `mlx-community/bge-m3-mlx-4bit`。

## 1. 基本概念

MLX 是 Apple 官方面向 Apple Silicon 的机器学习框架，`mlx-lm` 是基于 MLX 的大语言模型运行工具。

BGE-M3 embedding 使用 `mlx-embeddings` 加载 MLX safetensors，不走 `mlx_lm.server` 的 `/v1/embeddings`。系统侧通过 Python AI Service `/knowledge/embed` 统一封装。

本项目推荐两种使用方式：

| 方式 | 用途 | 特点 |
| --- | --- | --- |
| CLI 单次生成 | 临时测试、测速、抽取验证 | 命令执行一次，生成结束后进程退出 |
| HTTP Server | 接入系统、持续对话、OpenAI 兼容调用 | 启动后常驻，占用端口和内存 |

注意：`mlx_lm.server` 官方提示不建议直接作为生产服务暴露公网使用。本项目只建议绑定 `127.0.0.1`，由后端服务在本机或内网受控调用。

## 2. 环境准备

本项目当前 Python 虚拟环境位于仓库上级目录：

```zsh
/Users/lucas/Work/CompanyProject/.venv
```

如果在 `intelligent-customer-service-system` 项目目录下执行，可以这样激活：

```zsh
source ../.venv/bin/activate
```

如果在 `CompanyProject` 工作区根目录执行，可以这样激活：

```zsh
source .venv/bin/activate
```

安装或升级 MLX-LM：

```zsh
python -m pip install -U mlx-lm mlx-embeddings
```

检查是否安装成功：

```zsh
python -m mlx_lm.generate --help
python -m mlx_lm server --help
python - <<'PY'
import mlx_embeddings
print('mlx_embeddings ok')
PY
```

当前实测版本：

```text
mlx-lm 0.31.3
mlx 0.31.2
mlx-metal 0.31.2
```

## 3. 模型选择

常用模型示例：

| 模型 | 说明 | 适用场景 |
| --- | --- | --- |
| `mlx-community/Qwen3.5-2B-4bit` | 4bit 量化，体积约 1.75GB | 本地轻量抽取、问答、速度优先 |
| `mlx-community/Qwen3.5-2B-MLX-8bit` | 8bit 量化，体积约 2.69GB | 更接近高精度量化，下载更慢、内存更高 |
| `mlx-community/Qwen3.5-2B-6bit` | 6bit 量化 | 折中选择 |
| `mlx-community/bge-m3-mlx-4bit` | BGE-M3 4bit embedding，1024 维，体积约 321MB | 本地知识块向量、对话 query vector、Hybrid Retrieval |

注意：用户口头简称 `mlx-community/bge-m3-4bit` 在 HuggingFace 上没有精确仓库；实际可用模型名是 `mlx-community/bge-m3-mlx-4bit`。系统代码对前者做了别名兼容，但配置和落库统一使用实际模型名。

注意：MLX 模型和 Ollama GGUF 模型不是同一个文件格式。即使名称都叫 Qwen3.5-2B，也要区分：

| 路线 | 文件格式 | 缓存位置 |
| --- | --- | --- |
| Ollama | GGUF / Ollama blob | `~/.ollama/models/` |
| MLX | MLX safetensors / HuggingFace cache | `~/.cache/huggingface/hub/` |

## 4. 下载模型

MLX-LM 会在第一次运行模型时自动从 HuggingFace 下载。

推荐先做一次短生成来触发下载：

```zsh
python -m mlx_lm.generate \
  --model mlx-community/Qwen3.5-2B-4bit \
  --prompt "你好，只回答 ok" \
  --max-tokens 8 \
  --temp 0 \
  --verbose True
```

下载完成后，本地缓存目录通常类似：

```zsh
~/.cache/huggingface/hub/models--mlx-community--Qwen3.5-2B-4bit
```

查看模型缓存大小：

```zsh
du -sh ~/.cache/huggingface/hub/models--mlx-community--Qwen3.5-2B-4bit
```

查看是否存在未完成下载文件：

```zsh
find ~/.cache/huggingface/hub/models--mlx-community--Qwen3.5-2B-4bit -name '*.incomplete' -print
```

如果网络中断，重新执行同一个模型命令即可断点续传。

未登录 HuggingFace 时可能出现下载限速提示：

```text
Warning: You are sending unauthenticated requests to the HF Hub.
```

如果经常下载大模型，建议配置 `HF_TOKEN` 后再执行下载。

## 5. CLI 单次生成

CLI 适合临时验证模型输出、测试 prompt、测速。

### 5.1 普通对话

```zsh
python -m mlx_lm.generate \
  --model mlx-community/Qwen3.5-2B-4bit \
  --system-prompt "你是一个简洁的中文助手。" \
  --prompt "用一句话解释 MLX 是什么。" \
  --max-tokens 128 \
  --temp 0 \
  --chat-template-config '{"enable_thinking": false}' \
  --verbose True
```

### 5.2 实体关系抽取

Qwen3.5 系列默认可能输出 thinking 内容。做 JSON 抽取时建议显式关闭 thinking，并用 `{` 预填充响应，减少跑偏概率。

```zsh
prompt='请对下面这段中文进行实体和关系抽取。只输出 JSON，不要解释。JSON 格式：{"entities":[{"name":"","type":""}],"relations":[{"subject":"","predicate":"","object":""}]}。\n文本：张华管理先科制药有限公司，公司下有五个部门：财务，销售，研发，质检，售后。张华毕业于南京理工大学金融管理系硕士学位'

python -m mlx_lm generate \
  --model mlx-community/Qwen3.5-2B-4bit \
  --system-prompt '你是信息抽取器，只输出紧凑 JSON。' \
  --prompt "$prompt" \
  --prefill-response '{' \
  --max-tokens 256 \
  --temp 0 \
  --chat-template-config '{"enable_thinking": false}' \
  --verbose True
```

实测输出速度示例：

```text
Prompt: 104 tokens, 266.356 tokens-per-sec
Generation: 211 tokens, 63.711 tokens-per-sec
Peak memory: 1.257 GB
real 6.95
```

注意：结构化抽取时 `max-tokens` 不能太小。实测 `max_tokens=128` 会截断 JSON，建议从 `256` 起步；复杂文本可提高到 `512` 或 `1024`。

### 5.3 长 Prompt 推荐写法

长 Prompt 不建议直接粘到交互式 zsh 的单引号变量里。多行单引号在终端里会进入续行输入状态，提示符可能变成 `>` 或显示一大段 `>....`，只要中间少一个引号，后面的 `python -m mlx_lm ...` 就会被当成字符串的一部分。

推荐用 heredoc 写法：

```zsh
prompt=$(cat <<'EOF'
你是企业知识图谱系统的信息抽取引擎(OpenIE)。

任务：
从输入文本提取 entities 和 relations。

要求：
1. 只输出JSON
2. 禁止Markdown
3. 禁止解释
4. 禁止分析
5. 禁止推理过程
6. 禁止补充原文不存在的信息
7. 枚举必须拆分
8. 公司/部门/学校等必须拆成独立实体
9. relation只能使用：MANAGE, HAS_DEPARTMENT, GRADUATED_FROM, HAS_DEGREE, MAJOR, HAS_COMPONENT, RELATED_TO
10. entity.type只能使用：PERSON, ORG, DEPARTMENT, UNIVERSITY, MAJOR, DEGREE, UNKNOWN

最终JSON格式：
{
  "entities":[{"name":"","type":""}],
  "relations":[{"subject":"","predicate":"","object":""}]
}

文本：
张华管理先科制药有限公司，公司下有五个部门：财务，销售，研发，质检，售后。张华毕业于南京理工大学金融管理系硕士学位
EOF
)

python -m mlx_lm generate \
  --model mlx-community/Qwen3.5-2B-4bit \
  --system-prompt '你是OpenIE抽取引擎。禁止解释。禁止思考过程。禁止输出JSON之外内容。' \
  --prompt "$prompt" \
  --prefill-response '{' \
  --max-tokens 512 \
  --temp 0 \
  --top-p 1 \
  --chat-template-config '{"enable_thinking": false}' \
  --verbose False
```

如果使用 `--prefill-response '{'`，MLX-LM 的终端输出通常只打印模型继续生成的部分，也就是从 `"entities"` 开始，看起来像少了开头 `{`。实际完整响应应理解为：

```text
{ + 模型输出内容
```

如果需要终端直接显示完整 JSON，可以去掉 `--prefill-response '{'`，但模型更容易输出 Markdown 或解释文本；也可以在调用方把预填充的 `{` 拼回去。

长 Prompt 中包含错误示例、正确示例、枚举列表和最终格式时，Prompt token 会明显增加，输出也更长。建议把 `--max-tokens` 提到 `512`，否则容易出现 JSON 截断或关系缺失。

### 5.4 只保留干净 JSON 输出

如果直接在交互式终端粘贴长 Prompt，终端 scrollback 里会混入三类内容：

1. 你粘贴的原始命令和原始 Prompt，这是 zsh/VS Code 终端回显，不是模型输出。
2. `Fetching 10 files`、`Download complete` 等 HuggingFace / MLX-LM 进度信息，这些通常走 `stderr`。
3. `/usr/bin/time` 的 `real/user/sys` 计时信息，也走 `stderr`。

如果只想拿模型输出，不要在终端里直接观察整段 scrollback，建议把 Prompt、模型输出、运行日志分开保存。

```zsh
mkdir -p .runtime/mlx

cat > .runtime/mlx/openie_prompt.txt <<'EOF'
你是企业知识图谱系统的信息抽取引擎(OpenIE)。

任务：从输入文本提取 entities 和 relations。

要求：
1. 只输出JSON
2. 禁止Markdown
3. 禁止解释
4. 禁止分析
5. 禁止推理过程
6. 禁止补充原文不存在的信息
7. 枚举必须拆分
8. 公司/部门/学校等必须拆成独立实体
9. relation只能使用：MANAGE, HAS_DEPARTMENT, GRADUATED_FROM, HAS_DEGREE, MAJOR, HAS_COMPONENT, RELATED_TO
10. entity.type只能使用：PERSON, ORG, DEPARTMENT, UNIVERSITY, MAJOR, DEGREE, UNKNOWN

最终JSON格式：
{
  "entities":[{"name":"","type":""}],
  "relations":[{"subject":"","predicate":"","object":""}]
}

文本：
张华管理先科制药有限公司，公司下有五个部门：财务，销售，研发，质检，售后。张华毕业于南京理工大学金融管理系硕士学位
EOF

HF_HUB_DISABLE_PROGRESS_BARS=1 python -m mlx_lm generate \
  --model mlx-community/Qwen3.5-2B-4bit \
  --system-prompt '你是OpenIE抽取引擎。禁止解释。禁止思考过程。禁止输出JSON之外内容。' \
  --prompt "$(cat .runtime/mlx/openie_prompt.txt)" \
  --prefill-response '{' \
  --max-tokens 512 \
  --temp 0 \
  --top-p 1 \
  --chat-template-config '{"enable_thinking": false}' \
  --verbose False \
  > .runtime/mlx/openie_output.body \
  2> .runtime/mlx/openie_run.log

printf '{' > .runtime/mlx/openie_output.json
cat .runtime/mlx/openie_output.body >> .runtime/mlx/openie_output.json
```

查看干净 JSON：

```zsh
cat .runtime/mlx/openie_output.json
```

查看运行日志：

```zsh
cat .runtime/mlx/openie_run.log
```

如果不使用 `--prefill-response '{'`，则不需要执行最后两行拼接 `{` 的命令，可以直接把 stdout 写到 `.runtime/mlx/openie_output.json`。但结构化抽取场景下，保留 prefill 通常更稳。

## 6. 启动 HTTP 服务

HTTP Server 适合被后端服务调用。它提供 OpenAI 兼容接口，例如：

```text
GET  /v1/models
POST /v1/chat/completions
```

### 6.1 前台启动

```zsh
python -m mlx_lm server \
  --model mlx-community/Qwen3.5-2B-4bit \
  --host 127.0.0.1 \
  --port 18090 \
  --max-tokens 512 \
  --temp 0 \
  --chat-template-args '{"enable_thinking":false}' \
  --log-level INFO
```

启动成功后会看到类似日志：

```text
Starting httpd at 127.0.0.1 on port 18090...
```

### 6.2 后台启动

建议把日志和 PID 放到项目 `.runtime/mlx/` 目录。

```zsh
mkdir -p .runtime/mlx

nohup python -m mlx_lm server \
  --model mlx-community/Qwen3.5-2B-4bit \
  --host 127.0.0.1 \
  --port 18090 \
  --max-tokens 512 \
  --temp 0 \
  --chat-template-args '{"enable_thinking":false}' \
  --log-level INFO \
  > .runtime/mlx/qwen35-2b-4bit.log 2>&1 &

echo $! > .runtime/mlx/qwen35-2b-4bit.pid
```

如果是从 `CompanyProject` 根目录启动，并且要把日志写入当前项目，可以使用：

```zsh
mkdir -p intelligent-customer-service-system/.runtime/mlx

nohup .venv/bin/python -m mlx_lm server \
  --model mlx-community/Qwen3.5-2B-4bit \
  --host 127.0.0.1 \
  --port 18090 \
  --max-tokens 512 \
  --temp 0 \
  --chat-template-args '{"enable_thinking":false}' \
  --log-level INFO \
  > intelligent-customer-service-system/.runtime/mlx/qwen35-2b-4bit.log 2>&1 &

echo $! > intelligent-customer-service-system/.runtime/mlx/qwen35-2b-4bit.pid
```

## 7. 对话调用

### 7.1 查看模型列表

```zsh
curl -s http://127.0.0.1:18090/v1/models
```

成功示例：

```json
{
  "object": "list",
  "data": [
    {
      "id": "mlx-community/Qwen3.5-2B-4bit",
      "object": "model"
    }
  ]
}
```

### 7.2 普通聊天

```zsh
curl -s http://127.0.0.1:18090/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{
    "model": "mlx-community/Qwen3.5-2B-4bit",
    "messages": [
      {"role": "system", "content": "你是一个简洁的中文助手。"},
      {"role": "user", "content": "用一句话解释 MLX 是什么。"}
    ],
    "temperature": 0,
    "max_tokens": 128
  }'
```

### 7.3 实体关系抽取

```zsh
curl -s http://127.0.0.1:18090/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{
    "model": "mlx-community/Qwen3.5-2B-4bit",
    "messages": [
      {"role": "system", "content": "你是信息抽取器，只输出紧凑 JSON。"},
      {"role": "user", "content": "请对下面这段中文进行实体和关系抽取。只输出 JSON，不要解释。JSON 格式：{\"entities\":[{\"name\":\"\",\"type\":\"\"}],\"relations\":[{\"subject\":\"\",\"predicate\":\"\",\"object\":\"\"}]}。文本：张华管理先科制药有限公司，公司下有五个部门：财务，销售，研发，质检，售后。张华毕业于南京理工大学金融管理系硕士学位"}
    ],
    "temperature": 0,
    "max_tokens": 512
  }'
```

## 8. 查看状态

### 8.1 端口是否在监听

```zsh
lsof -nP -iTCP:18090 -sTCP:LISTEN
```

### 8.2 进程是否存在

如果使用 PID 文件启动：

```zsh
cat .runtime/mlx/qwen35-2b-4bit.pid
ps -p $(cat .runtime/mlx/qwen35-2b-4bit.pid) -o pid,etime,pcpu,pmem,command
```

按命令关键字查找：

```zsh
ps -axo pid,etime,pcpu,pmem,command | rg 'mlx_lm server|Qwen3.5-2B'
```

### 8.3 服务是否可用

```zsh
curl -s http://127.0.0.1:18090/v1/models
```

如果返回模型列表，说明服务可访问。

### 8.4 查看日志

```zsh
tail -f .runtime/mlx/qwen35-2b-4bit.log
```

常见日志包括：

```text
Prompt processing progress: ...
Prompt Cache: ...
POST /v1/chat/completions HTTP/1.1 200
```

### 8.5 查看缓存

```zsh
du -sh ~/.cache/huggingface/hub/models--mlx-community--Qwen3.5-2B-4bit
find ~/.cache/huggingface/hub/models--mlx-community--Qwen3.5-2B-4bit -name '*.incomplete' -print
```

## 9. 关闭服务

### 9.1 使用 PID 文件关闭

```zsh
kill $(cat .runtime/mlx/qwen35-2b-4bit.pid)
rm -f .runtime/mlx/qwen35-2b-4bit.pid
```

### 9.2 按端口关闭

```zsh
lsof -tiTCP:18090 -sTCP:LISTEN | xargs -r kill
```

### 9.3 强制关闭

普通 `kill` 无效时再使用：

```zsh
lsof -tiTCP:18090 -sTCP:LISTEN | xargs -r kill -9
```

### 9.4 确认已关闭

```zsh
lsof -nP -iTCP:18090 -sTCP:LISTEN
curl -s http://127.0.0.1:18090/v1/models
```

如果端口没有监听，`curl` 无法返回模型列表，即表示服务已关闭。

## 10. 常见问题

### 10.1 下载很慢

原因通常是 HuggingFace 未登录或网络不稳定。

处理方式：

```zsh
export HF_TOKEN='你的 HuggingFace Token'
python -m mlx_lm.generate --model mlx-community/Qwen3.5-2B-4bit --prompt "ok" --max-tokens 8
```

如果出现 `.incomplete` 文件，重新执行同一个命令即可断点续传。

### 10.2 输出 thinking 内容

Qwen3.5 默认可能输出 thinking。CLI 使用：

```zsh
--chat-template-config '{"enable_thinking": false}'
```

Server 使用：

```zsh
--chat-template-args '{"enable_thinking":false}'
```

### 10.3 JSON 被截断

提高 `max_tokens`：

```text
短文本抽取：256
中等文本抽取：512
长文本抽取：1024 或更高
```

### 10.4 端口被占用

查看占用进程：

```zsh
lsof -nP -iTCP:18090 -sTCP:LISTEN
```

关闭占用进程：

```zsh
lsof -tiTCP:18090 -sTCP:LISTEN | xargs -r kill
```

或者换一个端口启动：

```zsh
--port 18091
```

### 10.5 内存占用较高

查看进程资源：

```zsh
ps -axo pid,etime,pcpu,pmem,command | rg 'mlx_lm server|Qwen3.5-2B'
```

处理方式：

1. 优先使用 4bit 模型。
2. 降低并发参数。
3. 不需要时关闭 server。
4. 避免同时启动 Ollama、MLX、llama.cpp 多个大模型服务。

## 11. 本项目推荐配置

当前项目建议先使用：

```text
模型：mlx-community/Qwen3.5-2B-4bit
端口：18090
温度：0
max_tokens：512
thinking：false
绑定地址：127.0.0.1
```

推荐启动命令：

```zsh
mkdir -p .runtime/mlx

nohup python -m mlx_lm server \
  --model mlx-community/Qwen3.5-2B-4bit \
  --host 127.0.0.1 \
  --port 18090 \
  --max-tokens 512 \
  --temp 0 \
  --chat-template-args '{"enable_thinking":false}' \
  --log-level INFO \
  > .runtime/mlx/qwen35-2b-4bit.log 2>&1 &

echo $! > .runtime/mlx/qwen35-2b-4bit.pid
```

推荐健康检查：

```zsh
curl -s http://127.0.0.1:18090/v1/models
```

推荐关闭命令：

```zsh
kill $(cat .runtime/mlx/qwen35-2b-4bit.pid)
rm -f .runtime/mlx/qwen35-2b-4bit.pid
```

系统集成后，`scripts/start-all.sh` 默认会启动 MLX，并默认不再启动 Ollama：

```zsh
MLX_ENABLED=1
MLX_PORT=18090
MLX_MODEL=mlx-community/Qwen3.5-2B-4bit
OLLAMA_ENABLED=0
```

如需临时恢复 Ollama 运行能力，可以显式打开：

```zsh
OLLAMA_ENABLED=1 OLLAMA_MODEL=qwen2.5:1.5b ./scripts/start-all.sh start
```

系统中的 Ollama 适配代码保留，用于后续迁移到其他机器或其他 GGUF 运行环境。

## 12. 与 Ollama / llama.cpp 的当前对比结论

基于本机实测的 Qwen3.5-2B 抽取任务：

| 运行方式 | 状态 | 生成速度 | 结论 |
| --- | --- | --- | --- |
| Ollama `qwen3.5:2b` | 可用 | 约 18-19 tok/s | 稳定，但生成较慢 |
| MLX `Qwen3.5-2B-4bit` | 可用 | 约 63.7 tok/s | Apple Silicon 上更适合作为本地抽取路线 |
| llama.cpp 复用 Ollama GGUF | 当前不可用 | 无法测试 | 当前版本加载 qwen35 GGUF 元数据失败 |

当前建议：本项目知识抽取/结构化 JSON 输出优先评估 MLX 4bit 路线；Ollama 作为稳定兜底；llama.cpp 等待兼容的 GGUF 模型或更新版本后再评估。

## 13. BGE-M3 Embedding 常规操作

### 13.1 手动验证本地 embedding

首次执行会下载模型到 HuggingFace 缓存。

```zsh
python - <<'PY'
from pathlib import Path
from huggingface_hub import snapshot_download
from mlx_embeddings.utils import load_model, load_tokenizer
import mlx.core as mx

model_path = Path(snapshot_download(repo_id='mlx-community/bge-m3-mlx-4bit'))
model = load_model(model_path)
tokenizer = load_tokenizer(model_path)
tokens = tokenizer.encode('售后部门负责客户问题闭环。')
output = model(mx.array([tokens]))
embedding = output.last_hidden_state.mean(axis=1)
print(embedding.shape)
PY
```

期望输出：`(1, 1024)`。

### 13.2 通过系统接口验证 embedding

```zsh
curl -fsS -X POST http://127.0.0.1:8100/knowledge/embed \
  -H 'Content-Type: application/json' \
  -d '{"blocks":[{"blockId":"b1","text":"售后部门负责客户问题闭环。"}],"llmConfig":{"embeddingModel":"mlx-community/bge-m3-mlx-4bit","embeddingDim":1024}}'
```

接口应返回 `embeddingModel=mlx-community/bge-m3-mlx-4bit`、`embeddingDim=1024`，且 `embeddings[0].vector` 长度为 1024。

### 13.3 数据库维度与回填检查

BGE-M3 输出 1024 维，数据库列必须是 `vector(1024)`：

```zsh
docker compose --env-file .env -f infra/docker-compose.yml exec -T postgres \
  psql -U smart_support -d smart_support -Atc \
  "SELECT atttypid::regtype, atttypmod FROM pg_attribute WHERE attrelid='knowledge_block'::regclass AND attname='embedding';"
```

检查知识块是否已写入向量：

```zsh
docker compose --env-file .env -f infra/docker-compose.yml exec -T postgres \
  psql -U smart_support -d smart_support -Atc \
  "SELECT count(*), count(embedding) FROM knowledge_block;"
```

当前系统链路：知识录入在 parse 后调用 `/knowledge/embed` 回填 `knowledge_block.embedding`；聊天问答会把用户问题调用同一模型生成 query vector，再进入 Hybrid Retrieval。
