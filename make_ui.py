import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.patches as patches
import numpy as np

# 设置中文字体（尽量用系统自带的，如果不显示也无妨，看排版）
plt.rcParams['font.sans-serif'] = ['sans-serif']

def draw_bookshelf():
    fig, ax = plt.subplots(figsize=(6, 10), dpi=120)
    fig.patch.set_facecolor('#000000')
    ax.set_facecolor('#000000')
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 160)
    ax.axis('off')

    # 模拟 3x3 的漫画封面
    colors = ['#FF5733', '#33FF57', '#3357FF', '#F333FF', '#FFD733', '#33FFF0', '#8E44AD', '#E67E22', '#2C3E50']
    titles = ['进击的巨人', '赛博朋克', '沙丘', '火影忍者', '攻壳机动队', 'JoJo', '寄生兽', '阿基拉', '剑风传奇']
    
    idx = 0
    for row in range(3):
        for col in range(3):
            if idx >= len(colors): break
            x = 5 + col * 32
            y = 120 - row * 45
            
            # 封面矩形 (保留彩色)
            rect = patches.Rectangle((x, y), 26, 34, linewidth=0, facecolor=colors[idx])
            ax.add_patch(rect)
            
            # 封面下的极小书名 (白色)
            ax.text(x + 13, y - 6, titles[idx], color='#FFFFFF', fontsize=8, ha='center', va='top')
            idx += 1

    plt.title("Bookshelf (黑白画廊 / 保留原色)", color='white', pad=20)
    plt.savefig("ui_bookshelf.png", bbox_inches='tight', pad_inches=0.1, facecolor=fig.get_facecolor())
    print("✅ 已生成：书架概念图 ui_bookshelf.png")

def draw_reader():
    fig, ax = plt.subplots(figsize=(6, 12), dpi=120)
    fig.patch.set_facecolor('#000000')
    ax.set_facecolor('#000000')
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 200)
    ax.axis('off')

    # 模拟垂直连续的条漫
    # 第一张图
    rect1 = patches.Rectangle((0, 100), 100, 100, linewidth=0, facecolor='#444444')
    ax.add_patch(rect1)
    ax.text(50, 150, "[ 漫画图片 1 ]\n从上往下滑", color='white', ha='center', va='center', fontsize=12)
    
    # 第二张图（无缝衔接）
    rect2 = patches.Rectangle((0, 0), 100, 100, linewidth=0, facecolor='#222222')
    ax.add_patch(rect2)
    ax.text(50, 50, "[ 漫画图片 2 ]\n无缝连接", color='white', ha='center', va='center', fontsize=12)

    plt.title("Reader (垂直无缝连读)", color='white', pad=20)
    plt.savefig("ui_reader.png", bbox_inches='tight', pad_inches=0.1, facecolor=fig.get_facecolor())
    print("✅ 已生成：阅读器概念图 ui_reader.png")

draw_bookshelf()
draw_reader()
