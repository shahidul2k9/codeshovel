package com.felixgrund.codestory.ast.changes;

import com.felixgrund.codestory.ast.parser.Yfunction;

public class Ymovefromfile extends Ycrossfilechange {

	public Ymovefromfile(Yfunction matchedFunction, Yfunction compareFunction) {
		super(matchedFunction, compareFunction);
	}

}