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
 * <p>模型偶尔把列表项写成 "-内容"（减号后无空格）或 "-　内容"（全角空格），
 * CommonMark 不认这两种写法为列表，整段会按普通文本渲染，
 * 页面上原样出现 "-" 符号。这里在解析前逐行修复：
 * 行首列表符号后补一个半角空格（全角空格替换为半角）。
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
    // 无序列表：行首（≤3 空格缩进，与 CommonMark 列表定义一致）- / * 后缺半角空格时补上；
    // "- 内容"（已合法）与 "-- 文本"（删除线语法）不受影响
    lines[i] = lines[i]
      .replace(/^ {0,3}([-*])[ \t]*\u3000+(?=\S)/, '$1 ')
      .replace(/^ {0,3}([-*])(?=[^\s*-])/, '$1 ')
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
