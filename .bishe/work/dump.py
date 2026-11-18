import sys, docx
from docx.shared import Pt
p = sys.argv[1]
d = docx.Document(p)
print("="*70)
print("FILE:", p)
print("SECTIONS:", len(d.sections))
for i,s in enumerate(d.sections):
    print(f"  sec{i}: page={s.page_width.cm:.2f}x{s.page_height.cm:.2f}cm margins L{s.left_margin.cm:.2f} R{s.right_margin.cm:.2f} T{s.top_margin.cm:.2f} B{s.bottom_margin.cm:.2f} orient={s.orientation}")
print("-"*70)
print("BODY (in document order):")
from docx.oxml.ns import qn
body = d.element.body
idx_p = 0
ti = 0
for child in body.iterchildren():
    tag = child.tag.split('}')[1]
    if tag == 'p':
        para = docx.text.paragraph.Paragraph(child, d)
        txt = para.text
        st = para.style.name if para.style is not None else '?'
        # font info
        info=[]
        for r in para.runs[:2]:
            if r.font.name or r.font.size:
                info.append(f"{r.font.name}/{r.font.size.pt if r.font.size else '?'}")
        al = para.alignment
        fl = None
        try: fl = para.paragraph_format.first_line_indent
        except: pass
        ls = None
        try: ls = para.paragraph_format.line_spacing
        except: pass
        brk = 'PG_BREAK' if 'w:br' in child.xml and 'type="page"' in child.xml else ''
        if txt.strip() or brk:
            print(f"P{idx_p:03d} [{st}] align={al} ls={ls} fl={fl} {info} {brk} :: {txt}")
        else:
            print(f"P{idx_p:03d} [EMPTY] {brk}")
        idx_p += 1
    elif tag == 'tbl':
        print(f"TBL{ti}: {len(child.findall(qn('w:tr')))} rows")
        for ri,tr in enumerate(child.findall(qn('w:tr'))):
            cells=[]
            for tc in tr.findall(qn('w:tc')):
                cells.append(" ".join("".join(t.text or '' for t in tc.iter(qn('w:t'))).split()))
            print(f"   R{ri}: " + " | ".join(cells[:6]))
        ti += 1
    elif tag == 'sectPr':
        print("[SECTPR]")
