/**
 * 评论 @ 提及：内容的分段解析（渲染高亮）与输入框插入。
 * 提及词法：@ 后跟一段非空白、非常用标点的连续字符（支持中文昵称）。
 */

export interface MentionSegment {
  type: 'text' | 'mention'
  text: string
}

const MENTION_RE = /@[^\s@，。！？、；：…!?,.:;()（）[\]{}<>《》"'~～*#`|\\/=+]+/g

/** 把评论文本拆成 text / mention 段，mention 段以 @ 开头 */
export function splitMentions(content: string): MentionSegment[] {
  if (!content) return []
  const segments: MentionSegment[] = []
  let last = 0
  for (const match of content.matchAll(MENTION_RE)) {
    const start = match.index ?? 0
    if (start > last) {
      segments.push({ type: 'text', text: content.slice(last, start) })
    }
    segments.push({ type: 'mention', text: match[0] })
    last = start + match[0].length
  }
  if (last < content.length) {
    segments.push({ type: 'text', text: content.slice(last) })
  }
  return segments
}

/** 把 "@昵称 " 追加到输入框内容末尾（无空格分隔时自动补一个空格） */
export function appendMention(text: string, nickname: string): string {
  if (!text) return `@${nickname} `
  return text.endsWith(' ') || text.endsWith('\n')
    ? `${text}@${nickname} `
    : `${text} @${nickname} `
}
