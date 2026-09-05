# -*- coding: utf-8 -*-
"""派蒙6k VITS 轻量 TTS 侧车（ai-mall 内嵌版）：POST /tts {text, length_scale} -> audio/wav
用 MoeGoe 的 VITS 模型/文本模块，加载 paimon6k_390k.pth + paimon6k.json。
【相对路径】模型与 MoeGoe 库均相对本脚本所在目录读取（模型/MoeGoe 为受限资产，不入库，见 README）。
【端口可配】由 --port 参数或环境变量 VITS_PORT 指定（默认 9944）。
仅本地/受控使用；如需对外请自评合规。
"""
import io, os, sys, argparse

BASE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(BASE, 'MoeGoe'))

import torch
import soundfile as sf
from fastapi import FastAPI, Response
from pydantic import BaseModel

import utils
from models import SynthesizerTrn
from text import text_to_sequence

CFG = os.path.join(BASE, 'paimon6k.json')
PTH = os.path.join(BASE, 'paimon6k_390k.pth')

hps = utils.get_hparams_from_file(CFG)
n_speakers = hps.data.n_speakers if 'n_speakers' in hps.data.keys() else 0
n_symbols = len(hps.symbols) if 'symbols' in hps.keys() else 0
symbols = list(hps.symbols) if 'symbols' in hps.keys() else []
# 【正解】该模型 checkpoint 的 enc_p.emb 是 52 行，而 config 只列 50 个符号；
# 缺的 2 个是 \u300c「 和 \u300d」，应插在 …(\u2026) 与 ㄅ(\u3105) 之间（追加到尾部是错的）。
# 来源见 zixiiu/Digital_Life_Server#32 评论区 LaiWX 的解法。
if len(symbols) < 52:
    try:
        i = symbols.index('\u3105')
    except ValueError:
        i = len(symbols)
    symbols = symbols[:i] + ['\u300c', '\u300d'] + symbols[i:]
n_symbols = len(symbols)
cleaners = hps.data.text_cleaners
sr = hps.data.sampling_rate
emotion_embedding = hps.data.emotion_embedding if 'emotion_embedding' in hps.data.keys() else False
device = 'cuda' if torch.cuda.is_available() else 'cpu'

net_g = SynthesizerTrn(
    n_symbols,
    hps.data.filter_length // 2 + 1,
    hps.train.segment_size // hps.data.hop_length,
    n_speakers=n_speakers,
    emotion_embedding=emotion_embedding,
    **hps.model,
).to(device)
net_g.eval()
utils.load_checkpoint(PTH, net_g)

app = FastAPI()


class Req(BaseModel):
    text: str
    # VITS length_scale（=语速）：>1 变慢（音素时长拉长），1.0 原速。缺省按原速。
    length_scale: float = 1.0


def synth(text: str, length_scale: float = 1.0):
    if not symbols:
        raise ValueError('config 缺少 symbols')
    seq = text_to_sequence(text, symbols, cleaners)
    if not seq:
        raise ValueError('文本转序列为空')
    # 该模型 config 的 add_blank:true —— 训练时在音素间插入空白(id=0)，推理也必须同样插入，否则输出极短乱码
    blank_seq = []
    for s in seq:
        blank_seq.append(0)
        blank_seq.append(s)
    seq = blank_seq[1:]
    stn = torch.LongTensor(seq).to(device)
    x = stn.unsqueeze(0)
    x_len = torch.LongTensor([stn.size(0)]).to(device)
    sid = torch.LongTensor([0]).to(device)
    with torch.no_grad():
        audio = net_g.infer(
            x, x_len, sid=sid,
            noise_scale=0.667, noise_scale_w=0.8,
            length_scale=float(length_scale),
        )[0][0, 0].data.cpu().float().numpy()
    return audio, sr


@app.post('/tts')
def tts(req: Req):
    audio, _sr = synth(req.text, req.length_scale)
    buf = io.BytesIO()
    sf.write(buf, audio, _sr, format='WAV', subtype='PCM_16')
    return Response(content=buf.getvalue(), media_type='audio/wav')


@app.get('/health')
def health():
    return {'ok': True, 'device': device, 'n_symbols': n_symbols, 'sampling_rate': sr}


if __name__ == '__main__':
    import uvicorn
    parser = argparse.ArgumentParser(description='Paimon 6k VITS TTS sidecar (ai-mall embedded)')
    parser.add_argument('--port', type=int, default=int(os.environ.get('VITS_PORT', '9944')))
    args = parser.parse_args()
    uvicorn.run(app, host='127.0.0.1', port=args.port)
