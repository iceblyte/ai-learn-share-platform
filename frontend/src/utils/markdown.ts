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

export function renderMarkdown(text: string): string {
  if (!text) return ''
  const html = marked.parse(text, { async: false }) as string
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
