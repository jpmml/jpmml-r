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
 * @see <a href="https://github.com/wch/r-source/blob/trunk/src/main/serialize.c">serialize.c</a>
 */
public interface SerializationTypes {

	int ATTRLISTSXP = 239;
	int ATTRLANGSXP = 240;
	int BASEENVSXP = 241;
	int EMPTYENVSXP = 242;
	int BCREPREF = 243;
	int BCREPDEF = 244;
	int GENERICREFSXP = 245;
	int CLASSREFSXP = 246;
	int PERSISTSXP = 247;
	int PACKAGESXP = 248;
	int NAMESPACESXP = 249;
	int BASENAMESPACESXP = 250;
	int MISSINGARGSXP = 251;
	int UNBOUNDVALUESXP = 252;
	int GLOBALENVSXP = 253;
	int NILVALUESXP = 254;
	int REFSXP = 255;
}