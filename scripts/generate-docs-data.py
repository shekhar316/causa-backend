import os
import json

def generate():
    docs_dir = 'docs'
    data = {}

    # Root README
    if os.path.exists('README.md'):
        with open('README.md', 'r', encoding='utf-8') as f:
            data['root-readme'] = {
                'title': 'Causa AI — Main Overview',
                'category': 'Overview',
                'path': 'README.md',
                'content': f.read()
            }

    # GETTING_STARTED.md
    if os.path.exists('GETTING_STARTED.md'):
        with open('GETTING_STARTED.md', 'r', encoding='utf-8') as f:
            data['getting-started'] = {
                'title': 'Getting Started Guide',
                'category': 'Overview',
                'path': 'GETTING_STARTED.md',
                'content': f.read()
            }

    # All markdown files in docs/
    if os.path.exists(docs_dir):
        for root, dirs, files in os.walk(docs_dir):
            for file in sorted(files):
                if file.endswith('.md'):
                    full_path = os.path.join(root, file)
                    rel_path = os.path.relpath(full_path).replace('\\', '/')
                    doc_id = rel_path.replace('/', '_').replace('.', '_').replace('-', '_')
                    
                    parts = rel_path.split('/')
                    category = parts[1].replace('-', ' ').title() if len(parts) > 2 else 'General'
                    title = file.replace('.md', '').replace('-', ' ').replace('_', ' ').title()
                    
                    with open(full_path, 'r', encoding='utf-8') as f:
                        content = f.read()
                        
                    for line in content.splitlines():
                        if line.startswith('# '):
                            extracted_title = line.replace('# ', '').strip()
                            if extracted_title:
                                title = extracted_title
                            break
                            
                    data[doc_id] = {
                        'title': title,
                        'category': category,
                        'path': rel_path,
                        'content': content
                    }

    os.makedirs('website', exist_ok=True)
    with open('website/docs-data.js', 'w', encoding='utf-8') as f:
        f.write('window.CAUSA_DOCS = ' + json.dumps(data, indent=2) + ';')

    print(f'Successfully generated website/docs-data.js with {len(data)} documents.')

if __name__ == '__main__':
    generate()
