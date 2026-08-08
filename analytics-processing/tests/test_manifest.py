from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from goride_analytics.config import load_config
from goride_analytics.errors import DatasetValidationError
from goride_analytics.manifest import validate_dataset

from support import create_data_root, write_config


class DatasetManifestTests(unittest.TestCase):
    def test_validates_identity_source_and_checksum(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            data_root = create_data_root(base / "data")
            config = load_config(write_config(base / "profile.yml"))
            result = validate_dataset(config, {"TEST_ANALYTICS_ROOT": str(data_root)})

        self.assertEqual(result.status, "VALID")
        self.assertEqual(result.source_bytes, 26)
        self.assertEqual(result.source_relative_path, "raw/test-dataset/v1/source.zip")

    def test_rejects_manifest_without_source_relative_path(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            data_root = create_data_root(
                base / "data", include_source_relative_path=False
            )
            config = load_config(write_config(base / "profile.yml"))
            with self.assertRaises(DatasetValidationError) as raised:
                validate_dataset(config, {"TEST_ANALYTICS_ROOT": str(data_root)})

        self.assertEqual(raised.exception.code, "DATASET_MANIFEST_FIELD_MISSING")
        self.assertEqual(raised.exception.details["missing"], ["sourceRelativePath"])

    def test_rejects_checksum_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            data_root = create_data_root(base / "data")
            manifest_path = data_root / "manifests" / "datasets" / "test-v1.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["sha256"] = "0" * 64
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            config = load_config(write_config(base / "profile.yml"))
            with self.assertRaises(DatasetValidationError) as raised:
                validate_dataset(config, {"TEST_ANALYTICS_ROOT": str(data_root)})

        self.assertEqual(raised.exception.code, "DATASET_CHECKSUM_MISMATCH")

    def test_rejects_path_traversal(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            data_root = create_data_root(base / "data")
            manifest_path = data_root / "manifests" / "datasets" / "test-v1.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["sourceFile"] = "outside.zip"
            manifest["sourceRelativePath"] = "../../outside.zip"
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            config = load_config(write_config(base / "profile.yml"))
            with self.assertRaises(DatasetValidationError) as raised:
                validate_dataset(config, {"TEST_ANALYTICS_ROOT": str(data_root)})

        self.assertEqual(raised.exception.code, "DATASET_PATH_UNSAFE")

    def test_postgresql_profile_defers_connection_to_database_phase(self) -> None:
        root = Path(__file__).resolve().parents[1]
        config = load_config(root / "configs" / "goride-local.yml")
        result = validate_dataset(config, {})

        self.assertEqual(result.source_type, "postgresql")
        self.assertEqual(result.status, "CONFIG_VALID_CONNECTION_DEFERRED_TO_PHASE_3")


if __name__ == "__main__":
    unittest.main()
