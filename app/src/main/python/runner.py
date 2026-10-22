import sys
import io
import traceback

def run(code):
    buf = io.StringIO()
    old_stdout = sys.stdout
    old_stderr = sys.stderr
    sys.stdout = buf
    sys.stderr = buf
    try:
        exec(compile(code, "<dex>", "exec"), {})
    except Exception:
        traceback.print_exc(file=buf)
    finally:
        sys.stdout = old_stdout
        sys.stderr = old_stderr
    return buf.getvalue()