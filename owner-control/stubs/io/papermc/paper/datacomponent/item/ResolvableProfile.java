package io.papermc.paper.datacomponent.item;
public interface ResolvableProfile { static Builder resolvableProfile(){return null;} interface Builder { Builder name(String n); ResolvableProfile build(); } }
