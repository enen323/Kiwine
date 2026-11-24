export default function Footer() {
  const columns: { title: string; links: string[] }[] = [
    { title: '产品', links: ['模拟面试', '简历优化', '面试题库'] },
    { title: '公司', links: ['关于我们', '联系我们', '加入我们'] },
    { title: '资源', links: ['帮助中心', '博客', '社区'] },
    { title: '法律', links: ['隐私政策', '服务条款'] },
  ];

  return (
    <footer className="border-t bg-muted/30">
      <div className="mx-auto max-w-7xl px-4 py-12">
        <div className="grid grid-cols-2 gap-8 md:grid-cols-4">
          {columns.map((col) => (
            <div key={col.title}>
              <h4 className="mb-3 text-sm font-semibold text-foreground">{col.title}</h4>
              <ul className="space-y-2 text-sm text-muted-foreground">
                {col.links.map((link) => (
                  <li key={link}>
                    <a href="#" className="transition-colors hover:text-foreground">{link}</a>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
        <div className="mt-8 border-t pt-6 text-xs text-muted-foreground">
          © {new Date().getFullYear()} PrepDeck. All rights reserved.
        </div>
      </div>
    </footer>
  );
}
