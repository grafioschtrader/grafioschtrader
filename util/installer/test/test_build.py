"""Build and drift checks; all mutations stay in disposable source trees."""

from contextlib import redirect_stderr
import importlib.util
import io
from pathlib import Path
import py_compile
import shutil
import subprocess
import sys
import tempfile
import unittest


INSTALLER = Path(__file__).resolve().parents[1]
BASH = shutil.which("bash") or "bash"
SPEC = importlib.util.spec_from_file_location("installer_build", INSTALLER / "build.py")
BUILDER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BUILDER)


class BundleTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        shutil.copytree(INSTALLER / "src", self.root / "src")
        self.bundle = self.root / "gt-install.sh"

    def test_committed_bundle_matches_sources(self):
        self.assertEqual((INSTALLER / "gt-install.sh").read_bytes(), BUILDER.assemble(INSTALLER))

    def test_rebuild_is_identical_and_check_never_writes(self):
        self.assertEqual(BUILDER.build(self.root), 0)
        original = self.bundle.read_bytes()
        timestamp = self.bundle.stat().st_mtime_ns
        self.assertEqual(BUILDER.build(self.root), 0)
        self.assertEqual(BUILDER.build(self.root, check=True), 0)
        self.assertEqual(self.bundle.read_bytes(), original)
        self.assertEqual(self.bundle.stat().st_mtime_ns, timestamp)
        source = self.root / "src/10-inventory.sh"
        source.write_bytes(source.read_bytes() + b"# New inventory rule\n")
        with redirect_stderr(io.StringIO()):
            self.assertEqual(BUILDER.build(self.root, check=True), 1)
        self.assertEqual(self.bundle.read_bytes(), original)
        self.assertEqual(self.bundle.stat().st_mtime_ns, timestamp)
        self.assertEqual(BUILDER.build(self.root), 0)
        self.assertNotEqual(self.bundle.read_bytes(), original)

    def test_missing_bundle_check_does_not_create_it(self):
        with redirect_stderr(io.StringIO()):
            self.assertEqual(BUILDER.build(self.root, check=True), 1)
        self.assertFalse(self.bundle.exists())

    def test_extra_or_missing_module_fails_before_publication(self):
        BUILDER.build(self.root)
        original = self.bundle.read_bytes()
        extra = self.root / "src/98-extra.sh"
        extra.write_bytes(b"# unexpected\n")
        with self.assertRaisesRegex(ValueError, "module inventory"):
            BUILDER.build(self.root)
        extra.unlink()
        (self.root / "src/90-mail.sh").unlink()
        with self.assertRaisesRegex(ValueError, "module inventory"):
            BUILDER.build(self.root)
        self.assertEqual(self.bundle.read_bytes(), original)

    def test_invalid_python_or_heredoc_delimiter_cannot_replace_bundle(self):
        BUILDER.build(self.root)
        original = self.bundle.read_bytes()
        helper = self.root / "src/py/mail-probe.py"
        helper.write_bytes(b"if :\n")
        with self.assertRaises(SyntaxError):
            BUILDER.build(self.root)
        helper.write_bytes(b"PY\n")
        with self.assertRaisesRegex(ValueError, "heredoc delimiter"):
            BUILDER.build(self.root)
        self.assertEqual(self.bundle.read_bytes(), original)

    def test_unknown_markers_and_unused_helpers_fail(self):
        helper = self.root / "src/py/orphan.py"
        helper.write_bytes(b"pass\n")
        with self.assertRaisesRegex(ValueError, "Unused Python"):
            BUILDER.assemble(self.root)
        helper.unlink()
        source = self.root / "src/10-inventory.sh"
        source.write_bytes(source.read_bytes() + b"# @python ../escape.py\n")
        with self.assertRaisesRegex(ValueError, "unresolved Python"):
            BUILDER.assemble(self.root)

    def test_nonportable_source_text_fails(self):
        source = self.root / "src/10-inventory.sh"
        for data in (b"# CRLF\r\n", b"\xef\xbb\xbf# BOM\n", b"# no final newline"):
            source.write_bytes(data)
            with self.assertRaisesRegex(ValueError, "UTF-8"):
                BUILDER.assemble(self.root)

    def test_inline_python_retains_stdin_arguments_and_literal_quotes(self):
        helper = self.root / "src/py/npm-versions.py"
        helper.write_bytes(b'import sys\nprint("it\'s literal: " + sys.stdin.read() + sys.argv[1])\n')
        source = self.root / "src/00-common.sh"
        with source.open("a", encoding="utf-8", newline="\n") as stream:
            stream.write("\ngt_bundle_quote_test() { python3 -c '@python-inline npm-versions.py@' \"$1\"; }\n")
        BUILDER.build(self.root)
        result = subprocess.run(
            [BASH, "-c", 'GT_BUNDLE_TEST_PYTHON=$3; python3() { "$GT_BUNDLE_TEST_PYTHON" "$@"; }; '
             'GT_INSTALL_SOURCE_ONLY=1 source "$1"; gt_bundle_quote_test "$2"',
             "test", self.bundle.as_posix(), "$(not-executed)", Path(sys.executable).as_posix()],
            input="input: ", capture_output=True, text=True, check=True,
        )
        self.assertEqual(result.stdout, "it's literal: input: $(not-executed)\n")

    def test_downloaded_bundle_needs_no_source_tree_and_guard_does_not_run_main(self):
        BUILDER.build(self.root)
        shutil.rmtree(self.root / "src")
        result = subprocess.run([BASH, self.bundle.as_posix(), "--help"], capture_output=True, text=True, check=True)
        self.assertIn("Usage:", result.stdout)
        result = subprocess.run(
            [BASH, "-c", 'GT_INSTALL_SOURCE_ONLY=1 source "$1"; declare -F gt_reset gt_stage_contract gt_mail_probe',
             "test", self.bundle.as_posix()], capture_output=True, text=True, check=True,
        )
        self.assertEqual(result.stdout.splitlines(), ["gt_reset", "gt_stage_contract", "gt_mail_probe"])

    def test_python_sources_compile_without_caches_in_source_tree(self):
        sources = [INSTALLER / "build.py", *sorted((INSTALLER / "src/py").glob("*.py"))]
        for index, source in enumerate(sources):
            py_compile.compile(str(source), cfile=str(self.root / f"{index}.pyc"), doraise=True)

    def test_shell_sources_respect_the_line_limit(self):
        # The project formats at 120 characters; no shell formatter wraps lines, so this test keeps the limit.
        long = [f"{source.name}:{number}" for source in sorted((INSTALLER / "src").glob("*.sh"))
                for number, line in enumerate(source.read_text(encoding="utf-8").splitlines(), 1) if len(line) > 120]
        self.assertEqual(long, [])


if __name__ == "__main__":
    unittest.main()
