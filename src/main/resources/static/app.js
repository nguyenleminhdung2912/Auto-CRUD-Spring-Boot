document.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById('uploadForm');
  const status = document.getElementById('status');

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    status.textContent = 'Preparing...';

    const sqlInput = form.querySelector('input[name="sql"]');
    const overridesInput = form.querySelector('input[name="overrides"]');
    const projectName = form.querySelector('input[name="project-name"]').value.trim();

    if (!sqlInput.files.length) {
      status.textContent = 'Please select a SQL file.';
      return;
    }
    if (!projectName) {
      status.textContent = 'Please enter a project name.';
      return;
    }

    const fd = new FormData();
    fd.append('sql', sqlInput.files[0]);
    if (overridesInput.files.length) fd.append('overrides', overridesInput.files[0]);
    fd.append('project-name', projectName);

    try {
      status.textContent = 'Uploading...';
      const resp = await fetch('/api/generate/upload', {
        method: 'POST',
        body: fd
      });

      if (!resp.ok) {
        const text = await resp.text();
        status.textContent = 'Server error: ' + resp.status + ' - ' + (text || resp.statusText);
        return;
      }

      status.textContent = 'Generating ZIP...';
      const blob = await resp.blob();

      // try to read filename from Content-Disposition
      let filename = 'auto-crud.zip';
      const cd = resp.headers.get('Content-Disposition') || resp.headers.get('content-disposition');
      if (cd) {
        const m = /filename\*=UTF-8''([^;\n\r]+)/i.exec(cd) || /filename="?([^";]+)"?/i.exec(cd);
        if (m && m[1]) filename = decodeURIComponent(m[1]);
      }

      const link = document.createElement('a');
      link.href = URL.createObjectURL(blob);
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(link.href);

      status.textContent = 'Download started';
    } catch (err) {
      console.error(err);
      status.textContent = 'Error: ' + (err.message || err);
    }
  });
});
