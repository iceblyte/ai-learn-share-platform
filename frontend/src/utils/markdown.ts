/**
 * AI 回复 Markdown 渲染（GFM + XSS 净化）。
 *
 * <p>模型输出常含代码块、列表、加粗等 markdown 语法，纯文本展示会显得
 * 乱；这里统一转 HTML 并用 DOMPurify 净化（模型输出是不可信内容）。</p>
 */
import { marked } from 'marked'
import DOMPurify from 'dompurify'

marked.setOptions({
  gfm: true,
  breaks: true,
})

/**
 * 列表语法容错规范化。
 *
 * <p>模型偶尔把列表项写成 "-内容"（减号后无空格）、"-　内容"（全角空格）、
 * "-**加粗内容**"（减号紧贴加粗标记）或 "**-加粗内容**"（减号裹进加粗内），
 * CommonMark 均不认这些写法为列表，整段会按普通文本渲染，
 * 页面上原样出现 "-" 符号。这里在解析前逐行修复：
 * 行首列表符号后补一个半角空格（全角空格替换为半角；
 * 减号紧贴/裹进加粗标记时先归位到加粗外再补空格）。
 * 代码块围栏内的行不处理——diff/日志里行首 "-" 有自身语义。</p>
 */
function normalizeMarkdown(text: string): string {
  const lines = text.split('\n')
  let inCodeFence = false
  for (let i = 0; i < lines.length; i++) {
    if (/^\s*(```|~~~)/.test(lines[i])) {
      inCodeFence = !inCodeFence
      continue
    }
    if (inCodeFence) continue
    // 无序列表容错（按"最可能变体"优先级依次尝试）：
    // 1) "**-内容**"（减号裹进行首加粗）→ "- **内容**"：把减号移出加粗并补空格
    lines[i] = lines[i].replace(/^ {0,3}\*\*([-*])(?=[^\s*])/, '$1 **')
    // 2) "-**内容**"（减号后紧跟加粗标记）→ "- **内容**"：减号后补空格；
    //    "- ***内容***"（三星）有歧义，不动
    lines[i] = lines[i].replace(/^ {0,3}([-*])(?=\*\*(?!\*))/, '$1 ')
    // 3) "-　内容"（全角空格）→ "- 内容"
    lines[i] = lines[i].replace(/^ {0,3}([-*])[ \t]*\u3000+(?=\S)/, '$1 ')
    // 4) "- 内容"（已合法）与 "-- 文本"（行首连字符）不受影响；
    //    "*" 作列表符时不补空格（"*text*" 是斜体，不能当列表处理）
    lines[i] = lines[i].replace(/^ {0,3}(-)(?=[^\s*-])/, '$1 ')
    // 有序列表："1.内容" -> "1. 内容"；"4.6" 这类小数与 "1、内容" 不动
    lines[i] = lines[i].replace(/^ {0,3}(\d{1,9})\.(?!\d|\.\.)(?=[^\s.])/, '$1. ')
  }
  return lines.join('\n')
}

export function renderMarkdown(text: string): string {
  if (!text) return ''
  const html = marked.parse(normalizeMarkdown(text), { async: false }) as string
  return DOMPurify.sanitize(html, {
    // 链接在新窗口打开且不携带 referrer
    ADD_ATTR: ['target'],
  })
}

DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (node.tagName === 'A') {
    node.setAttribute('target', '_blank')
    node.setAttribute('rel', 'noopener noreferrer')
  }
})
