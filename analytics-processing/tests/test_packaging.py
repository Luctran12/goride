from __future__ import annotations

import tomllib
import unittest
from pathlib import Path


PACKAGE_ROOT = Path(__file__).resolve().parents[1]


def _locked_requirements(path: Path) -> set[str]:
    return {
        line.strip()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    }


class PackagingContractTests(unittest.TestCase):
    def test_pyproject_dependencies_match_lock_files(self) -> None:
        pyproject = tomllib.loads(
            (PACKAGE_ROOT / "pyproject.toml").read_text(encoding="utf-8")
        )
        project = pyproject["project"]

        self.assertEqual(
            set(project["dependencies"]),
            _locked_requirements(PACKAGE_ROOT / "requirements" / "runtime.lock"),
        )
        self.assertEqual(
            set(project["optional-dependencies"]["model-spike"]),
            _locked_requirements(PACKAGE_ROOT / "requirements" / "model-spike.lock"),
        )


if __name__ == "__main__":
    unittest.main()
