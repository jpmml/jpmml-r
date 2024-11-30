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

public class SerializationUtil {

	private SerializationUtil(){
	}

	static
	public int decodeType(int flags){
		return (flags & 255);
	}

	static
	public int decodeLevels(int flags){
		return (flags >> 12);
	}

	static
	public int unpackRefIndex(int flags){
		return (flags >> 8);
	}

	static
	public boolean isObject(int flags){
		return hasBit(flags, SerializationUtil.IS_OBJECT_BIT_MASK);
	}

	static
	public boolean hasAttributes(int flags){
		return hasBit(flags, SerializationUtil.HAS_ATTR_BIT_MASK);
	}

	static
	public int setHasAttributes(int flags){
		return setHasBit(flags, SerializationUtil.HAS_ATTR_BIT_MASK);
	}

	static
	public boolean hasTag(int flags){
		return hasBit(flags, SerializationUtil.HAS_TAG_BIT_MASK);
	}

	static
	public int setHasTag(int flags){
		return setHasBit(flags, SerializationUtil.HAS_TAG_BIT_MASK);
	}

	static
	public boolean isBytesCharset(int flags){
		return hasLevelBit(flags, SerializationUtil.BYTES_BIT_MASK);
	}

	static
	public boolean isLatin1Charset(int flags){
		return hasLevelBit(flags, SerializationUtil.LATIN1_BIT_MASK);
	}

	static
	public boolean isUTF8Charset(int flags){
		return hasLevelBit(flags, SerializationUtil.UTF8_BIT_MASK);
	}

	static
	private boolean hasLevelBit(int flags, int mask){
		return hasBit(decodeLevels(flags), mask);
	}

	static
	private boolean hasBit(int flags, int mask){
		return (flags & mask) == mask;
	}

	static
	private int setHasBit(int flags, int mask){
		return (flags | mask);
	}

	private static final int IS_OBJECT_BIT_MASK = (1 << 8);
	private static final int HAS_ATTR_BIT_MASK = (1 << 9);
	private static final int HAS_TAG_BIT_MASK = (1 << 10);

	private static final int BYTES_BIT_MASK = (1 << 1);
	private static final int LATIN1_BIT_MASK = (1 << 2);
	private static final int UTF8_BIT_MASK = (1 << 3);
	private static final int CACHED_BIT_MASK = (1 << 5);
	private static final int ASCII_BIT_MASK = (1 << 6);
}