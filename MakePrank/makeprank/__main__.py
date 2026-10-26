"""``python -m makeprank`` starts the Creator; ``python -m makeprank client`` the receiver."""
import sys

if len(sys.argv) > 1 and sys.argv[1] == "client":
    from .client_app import main
    main(sys.argv[2:])
else:
    from .creator_app import main
    main(sys.argv[1:])
