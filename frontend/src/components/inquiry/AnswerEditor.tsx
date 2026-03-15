'use client';

import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Image from '@tiptap/extension-image';
import Link from '@tiptap/extension-link';
import { Table } from '@tiptap/extension-table';
import { TableRow } from '@tiptap/extension-table/row';
import { TableCell } from '@tiptap/extension-table/cell';
import { TableHeader } from '@tiptap/extension-table/header';
import { handleImageUpload } from '@/lib/editor/imageUpload';
import { convertPlainTextToHtml } from '@/lib/editor/citationUtils';
import { useCallback, useEffect, useRef } from 'react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { showToast } from '@/lib/toast';
import {
  Bold,
  Italic,
  Heading2,
  Heading3,
  List,
  ListOrdered,
  Quote,
  Link2,
  ImagePlus,
  Mail,
  Loader2,
} from 'lucide-react';

interface AnswerEditorProps {
  content: string;
  draftFormat?: 'TEXT' | 'HTML';
  inquiryId: string;
  onChange?: (html: string) => void;
  onCopyForGmail?: () => void;
  editable?: boolean;
}

export default function AnswerEditor({
  content,
  draftFormat = 'TEXT',
  inquiryId,
  onChange,
  onCopyForGmail,
  editable = true,
}: AnswerEditorProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const uploadingRef = useRef(false);

  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        heading: { levels: [2, 3] },
        codeBlock: false,
        code: false,
        strike: false,
      }),
      Image.configure({
        inline: true,
        allowBase64: false,
      }),
      Link.configure({
        openOnClick: false,
      }),
      Table.configure({
        resizable: false,
      }),
      TableRow,
      TableCell,
      TableHeader,
    ],
    content: draftFormat === 'TEXT' ? convertPlainTextToHtml(content) : content,
    editable,
    onUpdate: ({ editor: ed }) => {
      onChange?.(ed.getHTML());
    },
  });

  // Update content when prop changes externally
  const prevContentRef = useRef(content);
  useEffect(() => {
    if (!editor) return;
    if (content !== prevContentRef.current) {
      prevContentRef.current = content;
      const html = draftFormat === 'TEXT' ? convertPlainTextToHtml(content) : content;
      editor.commands.setContent(html);
    }
  }, [content, draftFormat, editor]);

  // Update editable state
  useEffect(() => {
    if (editor) {
      editor.setEditable(editable);
    }
  }, [editable, editor]);

  // Cleanup
  useEffect(() => {
    return () => {
      editor?.destroy();
    };
  }, [editor]);

  const insertImage = useCallback(
    async (file: File) => {
      if (!editor || uploadingRef.current) return;
      uploadingRef.current = true;
      showToast('이미지 업로드 중...', 'info');
      try {
        const url = await handleImageUpload(file, inquiryId);
        editor.chain().focus().setImage({ src: url }).run();
        showToast('이미지가 삽입되었습니다', 'success');
      } catch (err) {
        showToast(err instanceof Error ? err.message : '이미지 업로드 실패', 'error');
      } finally {
        uploadingRef.current = false;
      }
    },
    [editor, inquiryId],
  );

  const handleDrop = useCallback(
    (view: unknown, event: DragEvent) => {
      const files = event.dataTransfer?.files;
      if (!files?.length) return false;
      const imageFile = Array.from(files).find(f =>
        ['image/png', 'image/jpeg'].includes(f.type),
      );
      if (!imageFile) return false;
      event.preventDefault();
      insertImage(imageFile);
      return true;
    },
    [insertImage],
  );

  const handlePaste = useCallback(
    (view: unknown, event: ClipboardEvent) => {
      const items = event.clipboardData?.items;
      if (!items) return false;
      for (const item of Array.from(items)) {
        if (item.type === 'image/png' || item.type === 'image/jpeg') {
          const file = item.getAsFile();
          if (file) {
            event.preventDefault();
            insertImage(file);
            return true;
          }
        }
      }
      return false;
    },
    [insertImage],
  );

  // Register drop/paste handlers
  useEffect(() => {
    if (!editor) return;
    editor.setOptions({
      editorProps: {
        handleDrop,
        handlePaste,
      },
    });
  }, [editor, handleDrop, handlePaste]);

  const handleLinkInsert = useCallback(() => {
    if (!editor) return;
    const previousUrl = editor.getAttributes('link').href ?? '';
    const url = window.prompt('URL을 입력하세요', previousUrl);
    if (url === null) return;
    if (url === '') {
      editor.chain().focus().extendMarkRange('link').unsetLink().run();
      return;
    }
    editor.chain().focus().extendMarkRange('link').setLink({ href: url }).run();
  }, [editor]);

  const handleImageButton = useCallback(() => {
    fileInputRef.current?.click();
  }, []);

  const handleFileChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];
      if (file) {
        insertImage(file);
      }
      e.target.value = '';
    },
    [insertImage],
  );

  if (!editor) {
    return (
      <div className="flex items-center justify-center p-8 text-sm text-muted-foreground">
        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
        에디터 로딩 중...
      </div>
    );
  }

  return (
    <div className="border rounded-md overflow-hidden">
      {/* Toolbar */}
      {editable && (
        <div className="flex items-center gap-1 p-1 border-b bg-muted/30 flex-wrap">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => editor.chain().focus().toggleBold().run()}
            className={cn(editor.isActive('bold') && 'bg-accent')}
            title="굵게"
          >
            <Bold className="h-4 w-4" />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => editor.chain().focus().toggleItalic().run()}
            className={cn(editor.isActive('italic') && 'bg-accent')}
            title="기울임"
          >
            <Italic className="h-4 w-4" />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}
            className={cn(editor.isActive('heading', { level: 2 }) && 'bg-accent')}
            title="제목 2"
          >
            <Heading2 className="h-4 w-4" />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()}
            className={cn(editor.isActive('heading', { level: 3 }) && 'bg-accent')}
            title="제목 3"
          >
            <Heading3 className="h-4 w-4" />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => editor.chain().focus().toggleBulletList().run()}
            className={cn(editor.isActive('bulletList') && 'bg-accent')}
            title="글머리 기호"
          >
            <List className="h-4 w-4" />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => editor.chain().focus().toggleOrderedList().run()}
            className={cn(editor.isActive('orderedList') && 'bg-accent')}
            title="번호 목록"
          >
            <ListOrdered className="h-4 w-4" />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => editor.chain().focus().toggleBlockquote().run()}
            className={cn(editor.isActive('blockquote') && 'bg-accent')}
            title="인용"
          >
            <Quote className="h-4 w-4" />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={handleLinkInsert}
            className={cn(editor.isActive('link') && 'bg-accent')}
            title="링크"
          >
            <Link2 className="h-4 w-4" />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={handleImageButton}
            title="이미지 삽입"
          >
            <ImagePlus className="h-4 w-4" />
          </Button>
          {onCopyForGmail && (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={onCopyForGmail}
              title="Gmail용 복사"
              className="ml-auto"
            >
              <Mail className="h-4 w-4 mr-1" />
              Gmail 복사
            </Button>
          )}
        </div>
      )}

      {/* Hidden file input */}
      <input
        ref={fileInputRef}
        type="file"
        accept="image/png,image/jpeg"
        className="hidden"
        onChange={handleFileChange}
      />

      {/* Editor content */}
      <EditorContent
        editor={editor}
        className="prose prose-sm max-w-none p-4 min-h-[300px] focus-within:outline-none [&_.ProseMirror]:outline-none [&_.ProseMirror]:min-h-[280px]"
      />
    </div>
  );
}
