import { useRef, type ChangeEvent } from 'react';
import { Camera, X, Circle, RectangleVertical } from 'lucide-react';
import { EditableText } from '../fields/editable-text';
import { EditableSelect } from '../fields/editable-select';
import { FieldWrapper } from '../fields/field-wrapper';
import { useResumeStore } from '../../../stores/resume-store';
import type { EditorSection, PersonalInfoContent } from '../../../types/editor';

interface Props {
  section: EditorSection;
  onUpdate: (content: Partial<PersonalInfoContent>) => void;
}

function resizeImage(file: File, maxSize: number): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = (e) => {
      const img = new Image();
      img.onload = () => {
        const canvas = document.createElement('canvas');
        let { width, height } = img;
        if (width > height) {
          if (width > maxSize) {
            height = Math.round((height * maxSize) / width);
            width = maxSize;
          }
        } else if (height > maxSize) {
          width = Math.round((width * maxSize) / height);
          height = maxSize;
        }
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext('2d')!;
        ctx.drawImage(img, 0, 0, width, height);
        resolve(canvas.toDataURL('image/jpeg', 0.85));
      };
      img.onerror = reject;
      img.src = e.target?.result as string;
    };
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

const GENDER_OPTIONS = ['男', '女', '保密'].map((v) => ({ label: v, value: v }));
const POLITICAL_OPTIONS = ['群众', '共青团员', '中共党员', '民主党派'].map((v) => ({ label: v, value: v }));
const ETHNICITY_OPTIONS = ['汉族', '少数民族'].map((v) => ({ label: v, value: v }));
const MARITAL_OPTIONS = ['未婚', '已婚', '离异'].map((v) => ({ label: v, value: v }));
const EDU_LEVEL_OPTIONS = [
  '高中', '大专', '本科', '硕士', '博士', '其他',
].map((v) => ({ label: v, value: v }));

export function PersonalInfoSection({ section, onUpdate }: Props) {
  const content = section.content as PersonalInfoContent;
  const fileInputRef = useRef<HTMLInputElement>(null);
  const { currentResume } = useResumeStore();
  const avatarStyle = currentResume?.themeConfig?.avatarStyle || 'oneInch';

  const updateAvatarStyle = (style: 'circle' | 'oneInch') => {
    if (!currentResume) return;
    useResumeStore.getState().updateTheme({ avatarStyle: style });
  };

  const handleAvatarChange = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const dataUrl = await resizeImage(file, 200);
    onUpdate({ avatar: dataUrl });
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  return (
    <div className="flex flex-col gap-2.5">
      <div className="flex items-center gap-3.5">
        <button
          type="button"
          className="flex size-[72px] shrink-0 cursor-pointer items-center justify-center overflow-hidden rounded-md border border-dashed border-border bg-muted/40"
          onClick={() => fileInputRef.current?.click()}
          style={{ borderRadius: avatarStyle === 'circle' ? '9999px' : '8px' }}
        >
          {content.avatar ? (
            <img src={content.avatar} alt="头像" className="size-full object-cover" />
          ) : (
            <Camera size={24} className="text-muted-foreground" />
          )}
        </button>
        <div className="flex flex-col items-start gap-2">
          <div className="inline-flex gap-0.5 rounded-md bg-muted p-0.5">
            {([
              { value: 'circle' as const, icon: Circle, label: '圆形' },
              { value: 'oneInch' as const, icon: RectangleVertical, label: '一寸' },
            ]).map(({ value, icon: Icon, label }) => (
              <button
                key={value}
                type="button"
                className={`inline-flex items-center gap-1 rounded px-3 py-1.5 text-[13px] cursor-pointer ${avatarStyle === value ? 'bg-background text-brand shadow-sm' : 'text-muted-foreground'}`}
                onClick={() => updateAvatarStyle(value)}
              >
                <Icon size={12} />
                {label}
              </button>
            ))}
          </div>
          {content.avatar && (
            <button
              type="button"
              className="inline-flex items-center gap-1 text-sm text-brand hover:text-brand/80"
              onClick={() => onUpdate({ avatar: '' })}
            >
              <X size={12} />
              移除头像
            </button>
          )}
        </div>
        <input
          ref={fileInputRef}
          type="file"
          accept="image/*"
          onChange={handleAvatarChange}
          style={{ display: 'none' }}
        />
      </div>

      <FieldWrapper>
        <EditableText label="姓名" value={content.fullName} onChange={(v) => onUpdate({ fullName: v })} />
        <EditableText label="职位" value={content.jobTitle} onChange={(v) => onUpdate({ jobTitle: v })} />
      </FieldWrapper>
      <FieldWrapper>
        <EditableText label="年龄" value={content.age || ''} onChange={(v) => onUpdate({ age: v })} />
        <EditableSelect label="性别" value={content.gender || ''} onChange={(v) => onUpdate({ gender: v })} options={GENDER_OPTIONS} />
      </FieldWrapper>
      <FieldWrapper>
        <EditableSelect label="政治面貌" value={content.politicalStatus || ''} onChange={(v) => onUpdate({ politicalStatus: v })} options={POLITICAL_OPTIONS} />
        <EditableSelect label="民族" value={content.ethnicity || ''} onChange={(v) => onUpdate({ ethnicity: v })} options={ETHNICITY_OPTIONS} />
      </FieldWrapper>
      <FieldWrapper>
        <EditableText label="籍贯" value={content.hometown || ''} onChange={(v) => onUpdate({ hometown: v })} />
        <EditableSelect label="婚姻状况" value={content.maritalStatus || ''} onChange={(v) => onUpdate({ maritalStatus: v })} options={MARITAL_OPTIONS} />
      </FieldWrapper>
      <FieldWrapper>
        <EditableText label="工作年限" value={content.yearsOfExperience || ''} onChange={(v) => onUpdate({ yearsOfExperience: v })} />
        <EditableSelect label="学历" value={content.educationLevel || ''} onChange={(v) => onUpdate({ educationLevel: v })} options={EDU_LEVEL_OPTIONS} />
      </FieldWrapper>
      <FieldWrapper>
        <EditableText label="邮箱" value={content.email} onChange={(v) => onUpdate({ email: v })} type="email" />
        <EditableText label="电话" value={content.phone} onChange={(v) => onUpdate({ phone: v })} type="tel" />
      </FieldWrapper>
      <FieldWrapper>
        <EditableText label="微信" value={content.wechat || ''} onChange={(v) => onUpdate({ wechat: v })} />
        <EditableText label="所在地" value={content.location} onChange={(v) => onUpdate({ location: v })} />
      </FieldWrapper>
      <FieldWrapper>
        <EditableText label="个人网站" value={content.website || ''} onChange={(v) => onUpdate({ website: v })} />
        <EditableText label="GitHub 主页" value={content.github || ''} onChange={(v) => onUpdate({ github: v })} />
      </FieldWrapper>
    </div>
  );
}
