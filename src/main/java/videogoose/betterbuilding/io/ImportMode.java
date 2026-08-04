package videogoose.betterbuilding.io;

/** How an import combines with what is already on the entity. */
public enum ImportMode {

	/** Delete the entity's existing annotations, then add the file's. */
	REPLACE("Replace", "Delete what is on this entity first"),

	/**
	 * Add the file's annotations, skipping any whose id is already present. Re-importing
	 * the same file twice is a no-op, which makes this the safe default for pulling in
	 * an updated copy of a set you already have.
	 */
	MERGE("Merge", "Add, skipping ones already present"),

	/**
	 * Add everything with fresh ids. Use when the same set should appear twice, or when
	 * importing someone else's file that happens to share ids with yours.
	 */
	APPEND("Append", "Add everything as new copies");

	private final String displayName;
	private final String description;

	ImportMode(String displayName, String description) {
		this.displayName = displayName;
		this.description = description;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getDescription() {
		return description;
	}
}
