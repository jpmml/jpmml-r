/*
 * Copyright (c) 2016 Villu Ruusmann
 *
 * This file is part of JPMML-R
 *
 * JPMML-R is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-R is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-R.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jpmml.rexp;

/**
 * @see https://github.com/wch/r-source/blob/trunk/src/include/Rinternals.h
 */
public interface SExpTypes {

	int NILSXP = 0; /* nil = NULL */
	int SYMSXP = 1; /* symbols */
	int LISTSXP = 2; /* lists of dotted pairs */
	int CLOSXP = 3; /* closures */
	int ENVSXP = 4; /* environments */
	int PROMSXP = 5; /* promises: [un]evaluated closure arguments */
	int LANGSXP = 6; /* language constructs (special lists) */
	int SPECIALSXP = 7; /* special forms */
	int BUILTINSXP = 8; /* builtin non-special forms */
	int CHARSXP = 9; /* "scalar" string type (internal only) */
	int LGLSXP = 10; /* logical vectors */
	/* 11 and 12 were factors and ordered factors in the 1990s */
	int INTSXP = 13; /* integer vectors */
	int REALSXP = 14; /* real variables */
	int CPLXSXP = 15; /* complex variables */
	int STRSXP = 16; /* string vectors */
	int DOTSXP = 17; /* dot-dot-dot object */
	int ANYSXP = 18; /* make "any" args work. */
	int VECSXP = 19; /* generic vectors */
	int EXPRSXP = 20; /* expressions vectors */
	int BCODESXP = 21; /* byte code */
	int EXTPTRSXP = 22; /* external pointer */
	int WEAKREFSXP = 23; /* weak reference */
	int RAWSXP = 24; /* raw bytes */
	int S4SXP = 25; /* S4, non-vector */

	/* used for detecting PROTECT issues in memory.c */
	int NEWSXP = 30; /* fresh node creaed in new page */
	int FREESXP = 31; /* node released by GC */

	int FUNSXP = 99; /* Closure or Builtin or Special */
}