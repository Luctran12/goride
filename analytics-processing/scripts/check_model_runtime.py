from __future__ import annotations

import json
import os
from importlib import metadata

os.environ.setdefault("LOKY_MAX_CPU_COUNT", "1")

def main() -> int:
    import numpy as np
    from sklearn.ensemble import HistGradientBoostingRegressor

    features = np.asarray(
        [[0.0, 0.0], [1.0, 0.0], [2.0, 1.0], [3.0, 1.0], [4.0, 0.0]],
        dtype=float,
    )
    target = np.asarray([0.0, 1.0, 2.0, 3.0, 4.0], dtype=float)
    model = HistGradientBoostingRegressor(
        max_iter=5,
        max_depth=2,
        learning_rate=0.1,
        random_state=5537,
    )
    first = model.fit(features, target).predict(features)
    second = model.fit(features, target).predict(features)
    deterministic = bool(np.array_equal(first, second))
    result = {
        "deterministic": deterministic,
        "model": "HistGradientBoostingRegressor",
        "numpy": metadata.version("numpy"),
        "scikitLearn": metadata.version("scikit-learn"),
    }
    print(json.dumps(result, sort_keys=True))
    return 0 if deterministic else 1


if __name__ == "__main__":
    raise SystemExit(main())
