package forge.adventure.scene;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import forge.Forge;
import forge.Graphics;
import forge.adventure.data.AdventureEventData;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.util.AdventureEventController;
import forge.adventure.util.Config;
import forge.adventure.util.Current;
import forge.assets.FImage;
import forge.assets.FSkinFont;
import forge.assets.FSkinImage;
import forge.card.CardEdition;
import forge.card.CardZoom;
import forge.deck.*;
import forge.game.GameType;
import forge.gamemodes.limited.BoosterDraft;
import forge.item.InventoryItem;
import forge.item.PaperCard;
import forge.itemmanager.*;
import forge.itemmanager.filters.ItemFilter;
import forge.localinstance.properties.ForgePreferences;
import forge.menu.FCheckBoxMenuItem;
import forge.menu.FDropDownMenu;
import forge.menu.FMenuItem;
import forge.menu.FPopupMenu;
import forge.model.FModel;
import forge.screens.FScreen;
import forge.screens.TabPageScreen;
import forge.toolbox.*;
import forge.util.Callback;
import forge.util.ItemPool;
import forge.util.Utils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.function.Function;

public class AdventureDeckEditor extends TabPageScreen<AdventureDeckEditor> {

    private static class ContentPreviewPage extends CatalogPage {

        Deck contents = new Deck();

        protected ContentPreviewPage(Deck cardsToShow) {
            super(ItemManagerConfig.ADVENTURE_STORE_POOL, Forge.getLocalizer().getMessage("lblInventory"), CATALOG_ICON);
            contents = cardsToShow;
            cardManager.setBtnAdvancedSearchOptions(false);
        }

        @Override
        protected void buildMenu(final FDropDownMenu menu, final PaperCard card) {
            //No menus to show here, this page is only to be used for previewing the known contents of a sealed pack
        }

        @Override
        public void refresh() {
            if (contents != null) cardManager.setPool(contents.getAllCardsInASinglePool());
        }
    }


    private static class DraftPackPage extends CatalogPage {
        protected DraftPackPage() {
            super(ItemManagerConfig.DRAFT_PACK, Forge.getLocalizer().getMessage("lblPackN", String.valueOf(1)), FSkinImage.PACK);
            cardManager.setShowRanking(true);
        }

        @Override
        public void refresh() {
            BoosterDraft draft = getDraft();
            if (draft == null || !draft.hasNextChoice()) {
                return;
            }

            CardPool pool = draft.nextChoice();

            if (pool == null || pool.isEmpty()) {
                return;
            }

            int packNumber = draft.getCurrentBoosterIndex() + 1;
            caption = Forge.getLocalizer().getMessage("lblPackN", String.valueOf(packNumber));
            cardManager.setPool(pool);
        }

        @Override
        protected void onCardActivated(PaperCard card) {
            super.onCardActivated(card);
            afterCardPicked(card);
        }

        @Override
        protected int getMaxMoveQuantity(boolean isAddMenu, boolean isAddSource) {
            return 1;
        }

        private void afterCardPicked(PaperCard card) {
            BoosterDraft draft = getDraft();
            assert draft != null;
            draft.setChoice(card);

            if (draft.hasNextChoice()) {
                refresh();
            } else {
                hideTab(); //hide this tab page when finished drafting
                parentScreen.completeDraft();
            }
        }

        @Override
        protected void buildMenu(final FDropDownMenu menu, final PaperCard card) {
            addItem(menu, Forge.getLocalizer().getMessage("lblAdd"), Forge.getLocalizer().getMessage("lblToMainDeck"), getMainDeckPage().getIcon(), true, true, new Callback<Integer>() {
                @Override
                public void run(Integer result) { //ignore quantity
                    mainDeckPage.addCard(card);

                    afterCardPicked(card);
                }
            });
            addItem(menu, Forge.getLocalizer().getMessage("lblAdd"), Forge.getLocalizer().getMessage("lbltosideboard"), getSideboardPage().getIcon(), true, true, new Callback<Integer>() {
                @Override
                public void run(Integer result) { //ignore quantity
                    getSideboardPage().addCard(card);
                    afterCardPicked(card);
                }
            });
        }
    }

    private static class StoreCatalogPage extends CatalogPage {
        protected StoreCatalogPage() {
            super(ItemManagerConfig.ADVENTURE_STORE_POOL, Forge.getLocalizer().getMessage("lblInventory"), CATALOG_ICON);
            Current.player().onGoldChange(() -> lblGold.setText(String.valueOf(AdventurePlayer.current().getGold())));
            cardManager.setBtnAdvancedSearchOptions(false);
        }

        @Override
        protected void buildMenu(final FDropDownMenu menu, final PaperCard card) {
            addItem(menu, Forge.getLocalizer().getMessage("lblSellFor"), String.valueOf(AdventurePlayer.current().cardSellPrice(card)), SIDEBOARD_ICON, false, true, new Callback<Integer>() {
                @Override
                public void run(Integer result) {
                    if (result == null || result <= 0) {
                        return;
                    }

                    if (!cardManager.isInfinite()) {
                        removeCard(card, result);
                    }
                    AdventurePlayer.current().sellCard(card, result, true);
                    lblGold.setText(String.valueOf(AdventurePlayer.current().getGold()));
                }
            });
        }

        @Override
        protected void onCardActivated(PaperCard card) {
            CardZoom.show(card); //This should probably be replaced with call to longPress, where to get x,y params?
        }

        @Override
        public void refresh() {
            cardManager.setPool(AdventurePlayer.current().getSellableCards());
        }
    }

    private static class CollectionCatalogPage extends CatalogPage {
        protected CollectionCatalogPage() {
            super(ItemManagerConfig.ADVENTURE_EDITOR_POOL, Forge.getLocalizer().getMessage("lblInventory"), CATALOG_ICON);
            cardManager.setBtnAdvancedSearchOptions(true);
            cardManager.setCatalogDisplay(true);
            cardManager.setShowNFSWatermark(true);
        }

        @Override
        protected void buildMenu(final FDropDownMenu menu, final PaperCard card) {
            addItem(menu, Forge.getLocalizer().getMessage("lblAdd"), Forge.getLocalizer().getMessage("lblTo") + " " + getMainDeckPage().cardManager.getCaption(), getMainDeckPage().getIcon(), true, true, new Callback<Integer>() {
                @Override
                public void run(Integer result) {
                    if (result == null || result <= 0) {
                        return;
                    }

                    if (!cardManager.isInfinite()) {
                        removeCard(card, result);
                    }
                    if (Current.player().autoSellCards.contains(card))
                        Current.player().autoSellCards.remove(card);
                    getMainDeckPage().addCard(card, result);
                }
            });
            if (getSideboardPage() != null) {
                addItem(menu, Forge.getLocalizer().getMessage("lblAdd"), Forge.getLocalizer().getMessage("lbltosideboard"), getSideboardPage().getIcon(), true, true, new Callback<Integer>() {
                    @Override
                    public void run(Integer result) {
                        if (result == null || result <= 0) {
                            return;
                        }

                        if (!cardManager.isInfinite()) {
                            removeCard(card, result);
                        }
                        if (Current.player().autoSellCards.contains(card))
                            Current.player().autoSellCards.remove(card);
                        getSideboardPage().addCard(card, result);
                    }
                });
            }

            int autoSellCount = Current.player().autoSellCards.count(card);
            int sellableCount = Current.player().getSellableCards().count(card);

            if (card.hasNoSellValue()) {
                FMenuItem unsellableIndicator = new FMenuItem(Forge.getLocalizer().getMessage("lblUnsellable"), null, null);
                unsellableIndicator.setEnabled(false);
                menu.addItem(unsellableIndicator);
            }

            if (sellableCount > 0) {
                FMenuItem moveToAutosell = new FMenuItem(Forge.getLocalizer().getMessage("lbltoSell", autoSellCount, sellableCount), Forge.hdbuttons ? FSkinImage.HDMINUS : FSkinImage.MINUS, e1 -> {
                    Current.player().autoSellCards.add(card);
                    refresh();
                });
                moveToAutosell.setEnabled(sellableCount - autoSellCount > 0);
                menu.addItem(moveToAutosell);

                FMenuItem moveToCatalog = new FMenuItem(Forge.getLocalizer().getMessage("lbltoInventory", autoSellCount, sellableCount), Forge.hdbuttons ? FSkinImage.HDPLUS : FSkinImage.PLUS, e1 -> {
                    Current.player().autoSellCards.remove(card);
                    refresh();
                });
                moveToCatalog.setEnabled(autoSellCount > 0);
                menu.addItem(moveToCatalog);
            }
        }

        @Override
        public void refresh() {
            final ItemPool<PaperCard> collectionPool = new ItemPool<>(PaperCard.class);

            final ItemPool<PaperCard> cardsInUse = new ItemPool<>(PaperCard.class);
            cardsInUse.addAllFlat(AdventurePlayer.current().getSelectedDeck().getAllCardsInASinglePool().toFlatList());

            if (showCollectionCards) {
                collectionPool.addAllFlat(AdventurePlayer.current().getCollectionCards(false).toFlatList());
            }
            else if(showNoSellCards) {
                collectionPool.addAll(AdventurePlayer.current().getCollectionCards(false).getFilteredPool(PaperCard::hasNoSellValue));
            }

            collectionPool.removeAllFlat(cardsInUse.toFlatList());
            if (!showNoSellCards) {
                collectionPool.removeIf(PaperCard::hasNoSellValue);
            }
            if (showAutoSellCards) {
                collectionPool.addAllFlat(AdventurePlayer.current().getAutoSellCards().toFlatList());
            }
            cardManager.setPool(collectionPool);
        }
    }

    public static BoosterDraft getDraft() {
        if (currentEvent == null)
            return null;
        return currentEvent.getDraft();
    }

    public static AdventureEventData currentEvent;

    public void setEvent(AdventureEventData event) {
        currentEvent = event;
    }

    public void completeDraft() {
        currentEvent.isDraftComplete = true;
        Deck[] opponentDecks = currentEvent.getDraft().getDecks();
        for (int i = 0; i < currentEvent.participants.length && i < opponentDecks.length; i++) {
            currentEvent.participants[i].setDeck(opponentDecks[i]);
        }
        currentEvent.draftedDeck = (Deck) currentEvent.registeredDeck.copyTo("Draft Deck");
        if (allowsAddBasic()) {
            launchBasicLandDialog();
            //Might be annoying if you haven't pruned your deck yet, but best to remind player that
            //this probably needs to be done since it's there since it's not normally part of Adventure
        }
        if (currentEvent.eventStatus == AdventureEventController.EventStatus.Entered) {
            currentEvent.eventStatus = AdventureEventController.EventStatus.Ready;
        }
    }

    private static final FileHandle deckIcon = Config.instance().getFile("ui/maindeck.png");
    private static final FImage MAIN_DECK_ICON = deckIcon.exists() ? new FImage() {
        @Override
        public float getWidth() {
            return 100f;
        }

        @Override
        public float getHeight() {
            return 100f;
        }

        @Override
        public void draw(Graphics g, float x, float y, float w, float h) {
            g.drawImage(Forge.getAssets().getTexture(deckIcon), x, y, w, h);
        }
    } : Forge.hdbuttons ? FSkinImage.HDLIBRARY : FSkinImage.DECKLIST;
    private static final FileHandle sideIcon = Config.instance().getFile("ui/sideboard.png");
    private static final FImage SIDEBOARD_ICON = sideIcon.exists() ? new FImage() {
        @Override
        public float getWidth() {
            return 100f;
        }

        @Override
        public float getHeight() {
            return 100f;
        }

        @Override
        public void draw(Graphics g, float x, float y, float w, float h) {
            g.drawImage(Forge.getAssets().getTexture(sideIcon), x, y, w, h);
        }
    } : Forge.hdbuttons ? FSkinImage.HDSIDEBOARD : FSkinImage.FLASHBACK;
    private static final float HEADER_HEIGHT = Math.round(Utils.AVG_FINGER_HEIGHT * 0.8f);
    private static final FileHandle binderIcon = Config.instance().getFile("ui/binder.png");
    private static final FImage CATALOG_ICON = binderIcon.exists() ? new FImage() {
        @Override
        public float getWidth() {
            return 100f;
        }

        @Override
        public float getHeight() {
            return 100f;
        }

        @Override
        public void draw(Graphics g, float x, float y, float w, float h) {
            g.drawImage(Forge.getAssets().getTexture(binderIcon), x, y, w, h);
        }
    } : FSkinImage.QUEST_BOX;
    private static final FileHandle sellIcon = Config.instance().getFile("ui/sell.png");
    private static final FLabel lblGold = new FLabel.Builder().text("0").icon(Forge.getAssets().getTexture(sellIcon) == null ? FSkinImage.QUEST_COINSTACK :
            new FImage() {
                @Override
                public float getWidth() {
                    return 100f;
                }

                @Override
                public float getHeight() {
                    return 100f;
                }

                @Override
                public void draw(Graphics g, float x, float y, float w, float h) {
                    g.drawImage(Forge.getAssets().getTexture(sellIcon), x, y, w, h);
                }
            }
    ).font(FSkinFont.get(16)).insets(new Vector2(Utils.scale(5), 0)).build();

    private static ItemPool<InventoryItem> decksUsingMyCards = new ItemPool<>(InventoryItem.class);
    private int selected = 0;

    public static void leave() {
        if (currentEvent != null && currentEvent.getDraft() != null && !currentEvent.isDraftComplete) {
            FOptionPane.showConfirmDialog(Forge.getLocalizer().getMessageorUseDefault("lblEndAdventureEventConfirm", "This will end the current event, and your entry fee will not be refunded.\n\nLeave anyway?"), Forge.getLocalizer().getMessage("lblLeaveDraft"), Forge.getLocalizer().getMessage("lblLeave"), Forge.getLocalizer().getMessage("lblCancel"), false, new Callback<Boolean>() {
                @Override
                public void run(Boolean result) {
                    if (result) {
                        currentEvent.eventStatus = AdventureEventController.EventStatus.Abandoned;
                        AdventurePlayer.current().getNewCards().clear();
                        Forge.clearCurrentScreen();
                        Forge.switchToLast();
                    }
                }
            });
        } else {
            AdventurePlayer.current().getNewCards().clear();
            Forge.clearCurrentScreen();
            Forge.switchToLast();
        }
    }

    @Override
    public void onActivate() {
        decksUsingMyCards = new ItemPool<>(InventoryItem.class);
        for (int i = 0; i < AdventurePlayer.current().getDeckCount(); i++) {
            final Deck deck = AdventurePlayer.current().getDeck(i);
            CardPool main = deck.getMain();
            for (final Map.Entry<PaperCard, Integer> e : main) {
                decksUsingMyCards.add(e.getKey());
            }
            if (deck.has(DeckSection.Sideboard)) {
                for (final Map.Entry<PaperCard, Integer> e : deck.get(DeckSection.Sideboard)) {
                    // only add card if we haven't already encountered it in main
                    if (!main.contains(e.getKey())) {
                        decksUsingMyCards.add(e.getKey());
                    }
                }
            }
        }
        lblGold.setText(String.valueOf(AdventurePlayer.current().getGold()));

//            if (currentEvent.registeredDeck!=null && !currentEvent.registeredDeck.isEmpty()){
//                //Use this deck instead of selected deck
//            }
    }

    public void refresh() {
        for (TabPage<AdventureDeckEditor> tabPage : tabPages) {
            ((DeckEditorPage) tabPage).initialize();
        }
        for (TabPage<AdventureDeckEditor> page : tabPages) {
            if (page instanceof CardManagerPage)
                ((CardManagerPage) page).refresh();
        }
    }

    public void showDefault() {
        if (catalogPage == null)
            return;
        catalogPage.showCollectionCards = true;
        catalogPage.showAutoSellCards = false;
        catalogPage.showNoSellCards = true;
        catalogPage.refresh();
    }

    public void showCollection() {
        if (catalogPage == null)
            return;
        catalogPage.showCollectionCards = true;
        catalogPage.showAutoSellCards = false;
        catalogPage.showNoSellCards = false;
        catalogPage.refresh();
    }

    public void showAutoSell() {
        if (catalogPage == null)
            return;
        catalogPage.showCollectionCards = false;
        catalogPage.showAutoSellCards = true;
        catalogPage.showNoSellCards = false;
        catalogPage.refresh();
    }

    public void showNoSell() {
        if (catalogPage == null)
            return;
        catalogPage.showCollectionCards = false;
        catalogPage.showAutoSellCards = false;
        catalogPage.showNoSellCards = true;
        catalogPage.refresh();
    }

    private static DeckEditorPage[] getPages(boolean isShop) {
        if (isShop) {
            return new DeckEditorPage[]{
                    new StoreCatalogPage()
            };
        } else {
            return new DeckEditorPage[]{
                    new CollectionCatalogPage(),
                    new DeckSectionPage(DeckSection.Main, ItemManagerConfig.ADVENTURE_EDITOR_POOL),
                    new DeckSectionPage(DeckSection.Sideboard, ItemManagerConfig.ADVENTURE_SIDEBOARD)};
        }
    }

    private static DeckEditorPage[] getPages(AdventureEventData event) {
        if (event == null) {
            return getPages(false);
        }
        if (event.format == AdventureEventController.EventFormat.Draft) {
            switch (event.eventStatus) {
                case Available:
                    return null;
                case Started:
                case Completed:
                case Abandoned:
                case Ready:
                    return new DeckEditorPage[]{
                            new DeckSectionPage(DeckSection.Main, ItemManagerConfig.DRAFT_POOL),
                            new DeckSectionPage(DeckSection.Sideboard, ItemManagerConfig.SIDEBOARD)};
                case Entered:
                    return new DeckEditorPage[]{
                            event.getDraft() != null ? (new DraftPackPage()) :
                                    new CatalogPage(ItemManagerConfig.DRAFT_PACK, Forge.getLocalizer().getMessage("lblInventory"), CATALOG_ICON),
                            new DeckSectionPage(DeckSection.Main, ItemManagerConfig.DRAFT_POOL),
                            new DeckSectionPage(DeckSection.Sideboard, ItemManagerConfig.SIDEBOARD)};
                default:
                    return new DeckEditorPage[]{
                            new CatalogPage(ItemManagerConfig.ADVENTURE_EDITOR_POOL, Forge.getLocalizer().getMessage("lblInventory"), CATALOG_ICON),
                            new DeckSectionPage(DeckSection.Main, ItemManagerConfig.ADVENTURE_EDITOR_POOL),
                            new DeckSectionPage(DeckSection.Sideboard, ItemManagerConfig.ADVENTURE_SIDEBOARD)};

            }
        } else if (event.format == AdventureEventController.EventFormat.Jumpstart) {
            return new DeckEditorPage[]{
                    new DeckSectionPage(DeckSection.Main, ItemManagerConfig.DRAFT_POOL),
                    new DeckSectionPage(DeckSection.Sideboard, ItemManagerConfig.SIDEBOARD)};
        } else return new DeckEditorPage[]{};
    }

    private static DeckEditorPage[] getPages(Deck deckToPreview) {
        return new DeckEditorPage[]{
                new ContentPreviewPage(deckToPreview)
        };
    }

    private CatalogPage catalogPage;
    private static DeckSectionPage mainDeckPage;
    private static DeckSectionPage sideboardPage;
    private DeckSectionPage commanderPage;

    protected final DeckHeader deckHeader = add(new DeckHeader());
    protected final FLabel lblName = deckHeader.add(new FLabel.Builder().font(FSkinFont.get(16)).insets(new Vector2(Utils.scale(5), 0)).build());
    private final FLabel btnMoreOptions = deckHeader.add(new FLabel.Builder().text("...").font(FSkinFont.get(20)).align(Align.center).pressedColor(Header.getBtnPressedColor()).build());


    boolean isShop;

    public AdventureDeckEditor(boolean createAsShop) {
        super(e -> leave(), getPages(createAsShop));
        isShop = createAsShop;
        doSetup();
    }

    public AdventureDeckEditor(AdventureEventData event) {
        super(e -> leave(), getPages(event));
        currentEvent = event;
        doSetup();
    }

    public AdventureDeckEditor(Deck deckToPreview) {
        super(e -> leave(), getPages(deckToPreview));
        doSetup();
    }

    public AdventureDeckEditor(boolean createAsShop, AdventureEventData event) {
        super(e -> leave(), event == null ? getPages(createAsShop) : getPages(event));
        doSetup();
    }

    private void doSetup() {
        //cache specific pages
        for (TabPage<AdventureDeckEditor> tabPage : tabPages) {
            if (tabPage instanceof CatalogPage) {
                catalogPage = (CatalogPage) tabPage;
            } else if (tabPage instanceof DeckSectionPage) {
                DeckSectionPage deckSectionPage = (DeckSectionPage) tabPage;
                switch (deckSectionPage.deckSection) {
                    case Main:
                    case Schemes:
                    case Planes:
                        mainDeckPage = deckSectionPage;
                        break;
                    case Sideboard:
                        sideboardPage = deckSectionPage;
                        break;
                    case Commander:
                        commanderPage = deckSectionPage;
                        break;
                    default:
                        break;
                }
            }
        }

        btnMoreOptions.setCommand(new FEvent.FEventHandler() {
            @Override
            public void handleEvent(FEvent e) {
                FPopupMenu menu = new FPopupMenu() {
                    @Override
                    protected void buildMenu() {
                        addItem(new FMenuItem(Forge.getLocalizer().getMessage("btnCopyToClipboard"), Forge.hdbuttons ? FSkinImage.HDEXPORT : FSkinImage.BLANK, e1 -> FDeckViewer.copyDeckToClipboard(getDeck())));
                        addItem(new FMenuItem(Forge.getLocalizer().getMessage("btnCopyCollectionToClipboard"), Forge.hdbuttons ? FSkinImage.HDEXPORT : FSkinImage.BLANK, e1 -> {
                            FDeckViewer.copyCollectionToClipboard(AdventurePlayer.current().getCards());
                        }));
                        if (allowsAddBasic()) {
                            FMenuItem addBasic = new FMenuItem(Forge.getLocalizer().getMessage("lblAddBasicLands"), FSkinImage.LANDLOGO, e1 -> launchBasicLandDialog());
                            addItem(addBasic);
                        }
                        if (!isShop && catalogPage != null && catalogPage instanceof CollectionCatalogPage && ItemManagerConfig.DRAFT_PACK != catalogPage.cardManager.getConfig()) {
                            // Add bulk sell menu option. This will sell all cards in the current filter.
                            int count = 0;
                            int value = 0;

                            for (Map.Entry<PaperCard, Integer> entry : catalogPage.cardManager.getFilteredItems()) {
                                value += AdventurePlayer.current().cardSellPrice(entry.getKey()) * entry.getValue();
                                count += entry.getValue();
                            }

                            final int finalCount = count;
                            final int finalValue = value;
                            addItem(new FMenuItem(Forge.getLocalizer().getMessage("lblSellCurrentFilters"), FSkinImage.QUEST_COINSTACK, e1 -> {
                                // aloww selling on catalog page only
                                if (catalogPage.cardManager.getCatalogSelectedIndex() == 1) {
                                    FOptionPane.showConfirmDialog(Forge.getLocalizer().getMessage("lblSellAllConfirm", finalCount, finalValue), Forge.getLocalizer().getMessage("lblSellCurrentFilters"), Forge.getLocalizer().getMessage("lblSell"), Forge.getLocalizer().getMessage("lblCancel"), false, new Callback<Boolean>() {
                                        @Override
                                        public void run(Boolean result) {
                                            if (result) {
                                                for (Map.Entry<PaperCard, Integer> entry : catalogPage.cardManager.getFilteredItems()) {
                                                    AdventurePlayer.current().sellCard(entry.getKey(), entry.getValue(), true);
                                                }
                                                catalogPage.refresh();
                                                lblGold.setText(String.valueOf(AdventurePlayer.current().getGold()));
                                            }
                                        }
                                    });
                                } else {
                                    FOptionPane.showErrorDialog(Forge.getLocalizer().getMessage("lblChoose") + " " +
                                        Forge.getLocalizer().getMessage("lblSellable"));
                                }
                            }));
                        }
                        ((DeckEditorPage) getSelectedPage()).buildDeckMenu(this);
                    }
                };
                menu.show(btnMoreOptions, 0, btnMoreOptions.getHeight());
            }
        });
    }


    protected void launchBasicLandDialog() {
        CardEdition defaultLandSet;
        Set<CardEdition> availableEditionCodes = new HashSet<>();

        if (currentEvent != null) {
            //suggest a random set from the ones used in the limited card pool that have all basic lands
            for (PaperCard p : currentEvent.registeredDeck.getAllCardsInASinglePool().toFlatList()) {
                availableEditionCodes.add(FModel.getMagicDb().getEditions().get(p.getEdition()));
            }
            defaultLandSet = CardEdition.Predicates.getRandomSetWithAllBasicLands(availableEditionCodes);
        } else {
            defaultLandSet = FModel.getMagicDb().getEditions().get("JMP");
        }

        if (defaultLandSet == null) {
            defaultLandSet = FModel.getMagicDb().getEditions().get("JMP");
        }

        List<CardEdition> unlockedEditions = new ArrayList<>();
        unlockedEditions.add(defaultLandSet);

        // Loop through Landscapes and add them to unlockedEditions
        if (currentEvent == null) {
            Map<String, CardEdition> editionsByName = new HashMap<>();
            for (CardEdition e : FModel.getMagicDb().getEditions()) {
                editionsByName.put(e.getName().toLowerCase(), e);
                editionsByName.put(e.getName().replace(":", "").toLowerCase(), e);
                editionsByName.put(e.getName().replace("'", "").toLowerCase(), e);
            }

            String sketchbookPrefix = "landscape sketchbook - ";
            for (String itemName : AdventurePlayer.current().getItems()) {
                if (!itemName.toLowerCase().startsWith(sketchbookPrefix)) {
                    continue;
                }

                // Extract the set name after the prefix
                String setName = itemName.substring(sketchbookPrefix.length()).trim();
                CardEdition edition = editionsByName.get(setName.toLowerCase());

                // Add the edition if found and it has basic lands
                if (edition != null && edition.hasBasicLands()) {
                    unlockedEditions.add(edition);
                }
            }
        }

        AddBasicLandsDialog dialog = new AddBasicLandsDialog(getDeck(), defaultLandSet, new Callback<CardPool>() {
            @Override
            public void run(CardPool landsToAdd) {
                getMainDeckPage().addCards(landsToAdd);
            }
        }, unlockedEditions);
        dialog.show();
        setSelectedPage(getMainDeckPage()); //select main deck page if needed so main deck is visible below dialog
    }

    protected boolean allowsAddBasic() {
        if (currentEvent == null)
            return true;
        if (!currentEvent.eventRules.allowsAddBasicLands)
            return false;
        if (currentEvent.eventStatus == AdventureEventController.EventStatus.Entered && currentEvent.isDraftComplete)
            return true;
        else return currentEvent.eventStatus == AdventureEventController.EventStatus.Ready;
    }

    @Override
    protected void doLayout(float startY, float width, float height) {
        if (deckHeader.isVisible()) {
            deckHeader.setBounds(0, startY, width, HEADER_HEIGHT);
            startY += HEADER_HEIGHT;
        }
        super.doLayout(startY, width, height);
    }

    public Deck getDeck() {
        if (currentEvent == null)
            return AdventurePlayer.current().getSelectedDeck();
        else
            return currentEvent.registeredDeck;
    }

    protected CatalogPage getCatalogPage() {
        return catalogPage;
    }

    protected static DeckSectionPage getMainDeckPage() {
        return mainDeckPage;
    }

    protected static DeckSectionPage getSideboardPage() {
        return sideboardPage;
    }

    protected DeckSectionPage getCommanderPage() {
        return commanderPage;
    }

    @Override
    public void onClose(final Callback<Boolean> canCloseCallback) {
        String errorMessage = GameType.Adventure.getDeckFormat().getDeckConformanceProblem(getDeck());
        if (errorMessage != null) {
            FOptionPane.showErrorDialog(errorMessage);
        }

        // if currentEvent is null, it should have been cleared or overwritten somehow
        if (currentEvent != null && currentEvent.getDraft() != null && !isShop) {
            if (currentEvent.isDraftComplete || canCloseCallback == null) {
                super.onClose(canCloseCallback); //can skip prompt if draft saved
                return;
            }
            FOptionPane.showConfirmDialog(Forge.getLocalizer().getMessageorUseDefault("lblEndAdventureEventConfirm", "This will end the current event, and your entry fee will not be refunded.\n\nLeave anyway?"), Forge.getLocalizer().getMessage("lblLeaveDraft"), Forge.getLocalizer().getMessage("lblLeave"), Forge.getLocalizer().getMessage("lblCancel"), false, canCloseCallback);
        }
    }

    @Override
    public FScreen getLandscapeBackdropScreen() {
        return null; //never use backdrop for editor
    }

    protected class DeckHeader extends FContainer {
        private DeckHeader() {
            setHeight(HEADER_HEIGHT);
        }

        boolean init;

        @Override
        public void drawBackground(Graphics g) {
            g.fillRect(Header.getBackColor(), 0, 0, getWidth(), HEADER_HEIGHT);
        }

        @Override
        public void drawOverlay(Graphics g) {
            float y = HEADER_HEIGHT - Header.LINE_THICKNESS / 2;
            g.drawLine(Header.LINE_THICKNESS, Header.getLineColor(), 0, y, getWidth(), y);
        }

        @Override
        protected void doLayout(float width, float height) {
            float x = 0;
            lblName.setBounds(0, 0, width - 2 * height, height);
            x += lblName.getWidth();
            //noinspection SuspiciousNameCombination
            x += height;
            //noinspection SuspiciousNameCombination
            btnMoreOptions.setBounds(x, 0, height, height);
            if (!init) {
                add(lblGold);
                init = true;
            }
            lblGold.setBounds(0, 0, width, height);
        }
    }

    protected static abstract class DeckEditorPage extends TabPage<AdventureDeckEditor> {
        protected DeckEditorPage(String caption0, FImage icon0) {
            super(caption0, icon0);
        }

        protected void buildDeckMenu(FPopupMenu menu) {
        }

        protected abstract void initialize();

        @Override
        public boolean fling(float velocityX, float velocityY) {
            return false; //prevent left/right swipe to change tabs since it doesn't play nice with item managers
        }
    }

    protected static abstract class CardManagerPage extends DeckEditorPage {
        private final ItemManagerConfig config;
        protected final CardManager cardManager = add(new CardManager(false));

        protected CardManagerPage(ItemManagerConfig config0, String caption0, FImage icon0) {
            super(caption0, icon0);
            config = config0;
            cardManager.setItemActivateHandler(e -> CardManagerPage.this.onCardActivated(cardManager.getSelectedItem()));
            cardManager.setContextMenuBuilder(new ItemManager.ContextMenuBuilder<PaperCard>() {
                @Override
                public void buildMenu(final FDropDownMenu menu, final PaperCard card) {
                    CardManagerPage.this.buildMenu(menu, card);
                }
            });
        }

        private final Function<Map.Entry<InventoryItem, Integer>, Comparable<?>> fnNewCompare = from -> AdventurePlayer.current().getNewCards().contains((PaperCard) from.getKey()) ? Integer.valueOf(1) : Integer.valueOf(0);
        private final Function<Map.Entry<? extends InventoryItem, Integer>, Object> fnNewGet = from -> AdventurePlayer.current().getNewCards().contains((PaperCard) from.getKey()) ? "NEW" : "";
        public static final Function<Map.Entry<InventoryItem, Integer>, Comparable<?>> fnDeckCompare = from -> decksUsingMyCards.count(from.getKey());
        public static final Function<Map.Entry<? extends InventoryItem, Integer>, Object> fnDeckGet = from -> Integer.valueOf(decksUsingMyCards.count(from.getKey())).toString();

        protected void initialize() {

            Map<ColumnDef, ItemColumn> colOverrides = new HashMap<>();
            ItemColumn.addColOverride(config, colOverrides, ColumnDef.NEW, fnNewCompare, fnNewGet);
            ItemColumn.addColOverride(config, colOverrides, ColumnDef.DECKS, fnDeckCompare, fnDeckGet);
            cardManager.setup(config, colOverrides);
        }

        protected boolean canAddCards() {
            return true;
        }

        public void addCard(PaperCard card) {
            addCard(card, 1);
        }

        public void addCard(PaperCard card, int qty) {
            if (canAddCards()) {
                cardManager.addItem(card, qty);
                updateCaption();
            }
        }


        public void removeCard(PaperCard card) {
            removeCard(card, 1);
        }

        public void removeCard(PaperCard card, int qty) {
            cardManager.removeItem(card, qty);
            updateCaption();
        }

        public void setCards(CardPool cards) {
            cardManager.setItems(cards);
            updateCaption();
        }

        protected void updateCaption() {
        }

        protected abstract void onCardActivated(PaperCard card);

        protected abstract void buildMenu(final FDropDownMenu menu, final PaperCard card);

        private ItemPool<PaperCard> getAllowedAdditions(Iterable<Map.Entry<PaperCard, Integer>> itemsToAdd, boolean isAddSource) {
            ItemPool<PaperCard> additions = new ItemPool<>(cardManager.getGenericType());
            Deck deck = parentScreen.getDeck();

            for (Map.Entry<PaperCard, Integer> itemEntry : itemsToAdd) {
                PaperCard card = itemEntry.getKey();

                int max;
                if (deck == null || card == null) {
                    max = Integer.MAX_VALUE;
                } else if (DeckFormat.canHaveAnyNumberOf(card)) {
                    max = Integer.MAX_VALUE;
                } else {
                    max = FModel.getPreferences().getPrefInt(ForgePreferences.FPref.DECK_DEFAULT_CARD_LIMIT);

                    Integer cardCopies = DeckFormat.canHaveSpecificNumberInDeck(card);
                    if (cardCopies != null) {
                        max = cardCopies;
                    }

                    max -= deck.getAllCardsInASinglePool().countAll(paperCard -> paperCard.getCardName().equals(card.getCardName()));
                }

                int qty;
                if (isAddSource) {
                    qty = itemEntry.getValue();
                } else {
                    try {
                        qty = parentScreen.getCatalogPage().cardManager.getItemCount(card);
                    } catch (Exception e) {
                        //prevent NPE
                        qty = 0;
                    }
                }
                if (qty > max) {
                    qty = max;
                }
                if (qty > 0) {
                    additions.add(card, qty);
                }
            }

            return additions;
        }

        protected int getMaxMoveQuantity(boolean isAddMenu, boolean isAddSource) {
            ItemPool<PaperCard> selectedItemPool = cardManager.getSelectedItemPool();
            if (isAddMenu) {
                selectedItemPool = getAllowedAdditions(selectedItemPool, isAddSource);
            }
            if (selectedItemPool.isEmpty()) {
                return 0;
            }
            int max = Integer.MAX_VALUE;
            for (Map.Entry<PaperCard, Integer> itemEntry : selectedItemPool) {
                if (itemEntry.getValue() < max) {
                    max = itemEntry.getValue();
                }
            }
            return max;
        }

        protected void addItem(FDropDownMenu menu, final String verb, String dest, FImage icon, boolean isAddMenu, boolean isAddSource, final Callback<Integer> callback) {
            final int max = getMaxMoveQuantity(isAddMenu, isAddSource);
            if (max == 0) {
                return;
            }

            String label = verb;
            if (!StringUtils.isEmpty(dest)) {
                label += " " + dest;
            }
            menu.addItem(new FMenuItem(label, icon, e -> {
                if (max == 1) {
                    callback.run(max);
                } else {
                    GuiChoose.getInteger(cardManager.getSelectedItem() + " - " + verb + " " + Forge.getLocalizer().getMessage("lblHowMany"), 1, max, 20, callback);
                }
            }));
        }

        protected void addCommanderItems(final FDropDownMenu menu, final PaperCard card, boolean isAddMenu, boolean isAddSource) {
            if (parentScreen.getCommanderPage() == null || card == null) {
                return;
            }
            boolean isLegalCommander;
            String captionSuffix = Forge.getLocalizer().getMessage("lblCommander");
            isLegalCommander = DeckFormat.Commander.isLegalCommander(card.getRules());
            if (isLegalCommander && !parentScreen.getCommanderPage().cardManager.getPool().contains(card)) {
                addItem(menu, "Set", "as " + captionSuffix, parentScreen.getCommanderPage().getIcon(), isAddMenu, isAddSource, new Callback<Integer>() {
                    @Override
                    public void run(Integer result) {
                        if (result == null || result <= 0) {
                            return;
                        }
                        setCommander(card);
                    }
                });
            }
            if (canHavePartnerCommander() && card.getRules().canBePartnerCommander()) {
                addItem(menu, "Set", "as Partner " + captionSuffix, parentScreen.getCommanderPage().getIcon(), isAddMenu, isAddSource, new Callback<Integer>() {
                    @Override
                    public void run(Integer result) {
                        if (result == null || result <= 0) {
                            return;
                        }
                        setPartnerCommander(card);
                    }
                });
            }
            if (canHaveSignatureSpell() && card.getRules().canBeSignatureSpell()) {
                addItem(menu, "Set", "as Signature Spell", FSkinImage.SORCERY, isAddMenu, isAddSource, new Callback<Integer>() {
                    @Override
                    public void run(Integer result) {
                        if (result == null || result <= 0) {
                            return;
                        }
                        setSignatureSpell(card);
                    }
                });
            }
        }

        protected boolean needsCommander() {
            return parentScreen.getCommanderPage() != null && parentScreen.getDeck().getCommanders().isEmpty();
        }

        protected boolean canHavePartnerCommander() {
            return parentScreen.getCommanderPage() != null && parentScreen.getDeck().getCommanders().size() == 1
                    && parentScreen.getDeck().getCommanders().get(0).getRules().canBePartnerCommander();
        }

        protected boolean canOnlyBePartnerCommander(final PaperCard card) {
            if (parentScreen.getCommanderPage() == null) {
                return false;
            }

            byte cmdCI = 0;
            for (final PaperCard p : parentScreen.getDeck().getCommanders()) {
                cmdCI |= p.getRules().getColorIdentity().getColor();
            }

            return !card.getRules().getColorIdentity().hasNoColorsExcept(cmdCI);
        }

        protected boolean canHaveSignatureSpell() {
            return parentScreen.getDeck().getOathbreaker() != null;
        }

        protected void setCommander(PaperCard card) {
            if (!cardManager.isInfinite()) {
                removeCard(card);
            }
            CardPool newPool = new CardPool();
            newPool.add(card);
            parentScreen.getCommanderPage().setCards(newPool);
            refresh(); //refresh so cards shown that match commander's color identity
        }

        protected void setPartnerCommander(PaperCard card) {
            if (!cardManager.isInfinite()) {
                removeCard(card);
            }
            parentScreen.getCommanderPage().addCard(card);
            refresh(); //refresh so cards shown that match commander's color identity
        }

        protected void setSignatureSpell(PaperCard card) {
            if (!cardManager.isInfinite()) {
                removeCard(card);
            }
            PaperCard signatureSpell = parentScreen.getDeck().getSignatureSpell();
            if (signatureSpell != null) {
                parentScreen.getCommanderPage().removeCard(signatureSpell); //remove existing signature spell if any
            }
            parentScreen.getCommanderPage().addCard(card);
            //refreshing isn't needed since color identity won't change from signature spell
        }

        public void refresh() {
            //not needed by default
        }

        @Override
        protected void doLayout(float width, float height) {
            float x = 0;
            if (Forge.isLandscapeMode()) { //add some horizontal padding in landscape mode
                x = ItemFilter.PADDING;
                width -= 2 * x;
            }
            cardManager.setBounds(x, 0, width, height);
        }
    }

    protected static class CatalogPage extends CardManagerPage {
        private boolean initialized, needRefreshWhenShown;

        boolean showCollectionCards = true, showAutoSellCards = false, showNoSellCards = true;

        protected CatalogPage(ItemManagerConfig config, String caption0, FImage icon0) {
            super(config, caption0, icon0);
            refresh();
        }

        private void setNextSelected() {
            setNextSelected(1);
        }

        private void setNextSelected(int val) {
            if (cardManager.getItemCount() < 1)
                return;
            if ((cardManager.getSelectedIndex() + val) < cardManager.getItemCount()) {
                cardManager.setSelectedIndex(cardManager.getSelectedIndex() + val);
            } else if ((cardManager.getSelectedIndex() + 1) < cardManager.getItemCount()) {
                cardManager.setSelectedIndex(cardManager.getSelectedIndex() + 1);
            }
        }

        private void setPreviousSelected() {
            setPreviousSelected(1);
        }

        private void setPreviousSelected(int val) {
            if (cardManager.getItemCount() < 1)
                return;
            if ((cardManager.getSelectedIndex() - val) > -1) {
                cardManager.setSelectedIndex(cardManager.getSelectedIndex() - val);
            } else if ((cardManager.getSelectedIndex() - 1) > -1) {
                cardManager.setSelectedIndex(cardManager.getSelectedIndex() - 1);
            }
        }

        @Override
        protected void initialize() {
            if (initialized) {
                return;
            } //prevent initializing more than once if deck changes
            initialized = true;

            super.initialize();
            cardManager.setCaption(getItemManagerCaption());

            if (!isVisible()) {
                needRefreshWhenShown = true;
            }
        }

        @Override
        protected boolean canAddCards() {
            if (needRefreshWhenShown) { //ensure refreshed before cards added if hasn't been refreshed yet
                needRefreshWhenShown = false;
                refresh();
            }
            return !cardManager.isInfinite();
        }

        protected String getItemManagerCaption() {
            return Forge.getLocalizer().getMessage("lblCards");
        }

        @Override
        public void setVisible(boolean visible0) {
            if (isVisible() == visible0) {
                return;
            }

            super.setVisible(visible0);
            if (visible0 && needRefreshWhenShown) {
                needRefreshWhenShown = false;
                refresh();
            }
        }

        @Override
        public void refresh() {
            final ItemPool<PaperCard> adventurePool = new ItemPool<>(PaperCard.class);

            final ItemPool<PaperCard> cardsInUse = new ItemPool<>(PaperCard.class);
            cardsInUse.addAll(AdventurePlayer.current().getSelectedDeck().getMain());
            cardsInUse.addAll(AdventurePlayer.current().getSelectedDeck().getOrCreate(DeckSection.Sideboard));

            if (showCollectionCards) {
                adventurePool.addAll(AdventurePlayer.current().getCollectionCards(false));
            }
            else if(showNoSellCards) {
                adventurePool.addAll(AdventurePlayer.current().getCollectionCards(false).getFilteredPool(PaperCard::hasNoSellValue));
            }

            adventurePool.removeAll(cardsInUse);
            if (!showNoSellCards) {
                adventurePool.removeIf(PaperCard::hasNoSellValue);
            }
            if (showAutoSellCards) {
                adventurePool.addAll(AdventurePlayer.current().getAutoSellCards());
            }

            cardManager.setPool(adventurePool);
        }

        @Override
        protected void onCardActivated(PaperCard card) {
            if (getMaxMoveQuantity(true, true) == 0) {
                return; //don't add card if maximum copies of card already in deck
            }
            if (needsCommander()) {
                setCommander(card); //handle special case of setting commander
                return;
            }
            if (canOnlyBePartnerCommander(card)) {
                return; //don't auto-change commander unexpectedly
            }
            DeckSectionPage main = getMainDeckPage();
            if (main == null)
                return;
            if (!cardManager.isInfinite()) {
                removeCard(card);
            }
            main.addCard(card);
        }

        @Override
        protected void buildMenu(final FDropDownMenu menu, final PaperCard card) {
            if (!needsCommander() && !canOnlyBePartnerCommander(card)) {
                if (!parentScreen.isShop) {
                    addItem(menu, Forge.getLocalizer().getMessage("lblAdd"), Forge.getLocalizer().getMessage("lblTo") + " " + getMainDeckPage().cardManager.getCaption(), getMainDeckPage().getIcon(), true, true, new Callback<Integer>() {
                        @Override
                        public void run(Integer result) {
                            if (result == null || result <= 0) {
                                return;
                            }

                            if (!cardManager.isInfinite()) {
                                removeCard(card, result);
                            }
                            getMainDeckPage().addCard(card, result);
                        }
                    });
                    if (getSideboardPage() != null) {
                        addItem(menu, Forge.getLocalizer().getMessage("lblAdd"), Forge.getLocalizer().getMessage("lbltosideboard"), getSideboardPage().getIcon(), true, true, new Callback<Integer>() {
                            @Override
                            public void run(Integer result) {
                                if (result == null || result <= 0) {
                                    return;
                                }

                                if (!cardManager.isInfinite()) {
                                    removeCard(card, result);
                                }
                                getSideboardPage().addCard(card, result);
                            }
                        });
                    }
                }
            }


            addCommanderItems(menu, card, true, true);

        }

        @Override
        protected void buildDeckMenu(FPopupMenu menu) {
            if (cardManager.getConfig().getShowUniqueCardsOption()) {
                menu.addItem(new FCheckBoxMenuItem(Forge.getLocalizer().getMessage("lblUniqueCardsOnly"), cardManager.getWantUnique(), e -> {
                    boolean wantUnique = !cardManager.getWantUnique();
                    cardManager.setWantUnique(wantUnique);
                    CatalogPage.this.refresh();
                    cardManager.getConfig().setUniqueCardsOnly(wantUnique);
                }));
            }
        }
    }

    protected static class DeckSectionPage extends CardManagerPage {
        private final String captionPrefix;
        private final DeckSection deckSection;

        private void setNextSelected() {
            setNextSelected(1);
        }

        private void setNextSelected(int val) {
            if (cardManager.getItemCount() < 1)
                return;
            if ((cardManager.getSelectedIndex() + val) < cardManager.getItemCount()) {
                cardManager.setSelectedIndex(cardManager.getSelectedIndex() + val);
            } else if ((cardManager.getSelectedIndex() + 1) < cardManager.getItemCount()) {
                cardManager.setSelectedIndex(cardManager.getSelectedIndex() + 1);
            }
        }

        private void setPreviousSelected() {
            setPreviousSelected(1);
        }

        private void setPreviousSelected(int val) {
            if (cardManager.getItemCount() < 1)
                return;
            if ((cardManager.getSelectedIndex() - val) > -1) {
                cardManager.setSelectedIndex(cardManager.getSelectedIndex() - val);
            } else if ((cardManager.getSelectedIndex() - 1) > -1) {
                cardManager.setSelectedIndex(cardManager.getSelectedIndex() - 1);
            }
        }

        protected DeckSectionPage(DeckSection deckSection0, ItemManagerConfig config) {
            super(config, null, null);

            deckSection = deckSection0;
            switch (deckSection) {
                default:
                case Main:
                    captionPrefix = Forge.getLocalizer().getMessage("lblMain");
                    cardManager.setCaption(Forge.getLocalizer().getMessage("ttMain"));
                    icon = MAIN_DECK_ICON;
                    cardManager.setBtnAdvancedSearchOptions(true);
                    cardManager.setCatalogDisplay(false);
                    break;
                case Sideboard:
                    captionPrefix = Forge.getLocalizer().getMessage("lblSide");
                    cardManager.setCaption(Forge.getLocalizer().getMessage("lblSideboard"));
                    icon = SIDEBOARD_ICON;
                    cardManager.setBtnAdvancedSearchOptions(false);
                    cardManager.setCatalogDisplay(false);
                    break;
                case Commander:
                    captionPrefix = Forge.getLocalizer().getMessage("lblCommander");
                    cardManager.setCaption(Forge.getLocalizer().getMessage("lblCommander"));
                    icon = FSkinImage.COMMANDER;
                    cardManager.setBtnAdvancedSearchOptions(true);
                    cardManager.setCatalogDisplay(false);
                    break;
            }
        }

        @Override
        protected void initialize() {
            super.initialize();
            cardManager.setPool(parentScreen.getDeck().getOrCreate(deckSection));
            updateCaption();
        }

        @Override
        protected void updateCaption() {
            if (deckSection == DeckSection.Commander) {
                caption = captionPrefix; //don't display count for commander section since it won't be more than 1
            } else {
                caption = captionPrefix + " (" + parentScreen.getDeck().get(deckSection).countAll() + ")";
            }
        }

        @Override
        protected void onCardActivated(PaperCard card) {
            CatalogPage catalog = parentScreen == null ? null : parentScreen.getCatalogPage();
            switch (deckSection) {
                case Main:
                case Planes:
                case Schemes:
                    removeCard(card);
                    if (currentEvent == null || currentEvent.getDraft() == null) {
                        if (catalog != null)
                            catalog.addCard(card);
                    } else if (getSideboardPage() != null) {
                        getSideboardPage().addCard(card);
                    }
                    break;
                case Sideboard:
                    removeCard(card);
                    getMainDeckPage().addCard(card);
                    break;
                default:
                    break;
            }
        }

        @Override
        protected void buildMenu(final FDropDownMenu menu, final PaperCard card) {
            CatalogPage catalog = parentScreen == null ? null : parentScreen.getCatalogPage();
            switch (deckSection) {
                case Main:
                    addItem(menu, Forge.getLocalizer().getMessage("lblAdd"), null, Forge.hdbuttons ? FSkinImage.HDPLUS : FSkinImage.PLUS, true, false, new Callback<Integer>() {
                        @Override
                        public void run(Integer result) {
                            if (result == null || result <= 0) {
                                return;
                            }

                            if (catalog != null) {
                                catalog.removeCard(card, result);
                                addCard(card, result);
                            }
                        }
                    });
                    if (currentEvent == null) {
                        addItem(menu, Forge.getLocalizer().getMessage("lblRemove"), null, Forge.hdbuttons ? FSkinImage.HDMINUS : FSkinImage.MINUS, false, false, new Callback<Integer>() {
                            @Override
                            public void run(Integer result) {
                                if (result == null || result <= 0) {
                                    return;
                                }

                                if (catalog != null) {
                                    removeCard(card, result);
                                    catalog.addCard(card, result);
                                }
                            }
                        });
                    }
                    if (getSideboardPage() != null) {
                        addItem(menu, Forge.getLocalizer().getMessage("lblMove"), Forge.getLocalizer().getMessage("lbltosideboard"), getSideboardPage().getIcon(), false, false, new Callback<Integer>() {
                            @Override
                            public void run(Integer result) {
                                if (result == null || result <= 0) {
                                    return;
                                }

                                removeCard(card, result);
                                getSideboardPage().addCard(card, result);
                            }
                        });
                    }
                    addCommanderItems(menu, card, false, false);
                    break;
                case Sideboard:
                    addItem(menu, Forge.getLocalizer().getMessage("lblAdd"), null, Forge.hdbuttons ? FSkinImage.HDPLUS : FSkinImage.PLUS, true, false, new Callback<Integer>() {
                        @Override
                        public void run(Integer result) {
                            if (result == null || result <= 0) {
                                return;
                            }

                            if (catalog != null) {
                                catalog.removeCard(card, result);
                                addCard(card, result);
                            }
                        }
                    });
                    if (currentEvent == null) {
                        addItem(menu, Forge.getLocalizer().getMessage("lblRemove"), null, Forge.hdbuttons ? FSkinImage.HDMINUS : FSkinImage.MINUS, false, false, new Callback<Integer>() {
                            @Override
                            public void run(Integer result) {
                                if (result == null || result <= 0) {
                                    return;
                                }

                                if (catalog != null) {
                                    removeCard(card, result);
                                    catalog.addCard(card, result);
                                }
                            }
                        });
                    }
                    addItem(menu, Forge.getLocalizer().getMessage("lblMove"), Forge.getLocalizer().getMessage("lblToMainDeck"), getMainDeckPage().getIcon(), false, false, new Callback<Integer>() {
                        @Override
                        public void run(Integer result) {
                            if (result == null || result <= 0) {
                                return;
                            }

                            removeCard(card, result);
                            getMainDeckPage().addCard(card, result);
                        }
                    });
                    addCommanderItems(menu, card, false, false);
                    break;
                case Commander:
                    if (isPartnerCommander(card)) {
                        addItem(menu, Forge.getLocalizer().getMessage("lblRemove"), null, Forge.hdbuttons ? FSkinImage.HDMINUS : FSkinImage.MINUS, false, false, new Callback<Integer>() {
                            @Override
                            public void run(Integer result) {
                                if (result == null || result <= 0) {
                                    return;
                                }

                                if (catalog != null) {
                                    removeCard(card, result);
                                    catalog.refresh(); //refresh so commander options shown again
                                    if (parentScreen != null)
                                        parentScreen.setSelectedPage(catalog);
                                }
                            }
                        });
                    }
                    break;
                default:
                    break;
            }
        }

        public void addCards(Iterable<Map.Entry<PaperCard, Integer>> cards) {
            if (canAddCards()) {
                cardManager.addItems(cards);
                //parentScreen.getEditorType().getController().notifyModelChanged();
                updateCaption();
            }
        }

        private boolean isPartnerCommander(final PaperCard card) {
            if (parentScreen.getCommanderPage() == null || parentScreen.getDeck().getCommanders().isEmpty()) {
                return false;
            }

            PaperCard firstCmdr = parentScreen.getDeck().getCommanders().get(0);
            return !card.getName().equals(firstCmdr.getName());
        }
    }

    @Override
    public boolean keyDown(int keyCode) {
        if (keyCode == Input.Keys.BUTTON_SELECT) {
            return this.tabHeader.btnBack.trigger();
        } else if (keyCode == Input.Keys.BUTTON_R1) {
            if (getSelectedPage() instanceof CatalogPage)
                ((CatalogPage) getSelectedPage()).cardManager.closeMenu();
            else if (getSelectedPage() instanceof DeckSectionPage)
                ((DeckSectionPage) getSelectedPage()).cardManager.closeMenu();
            selected++;
            if (selected > 2)
                selected = 0;
            setSelectedPage(tabPages.get(selected));
            if (getSelectedPage() instanceof CatalogPage) {
                ((CatalogPage) getSelectedPage()).cardManager.getConfig().setPileBy(null);
                ((CatalogPage) getSelectedPage()).cardManager.setHideFilters(true);
            } else if (getSelectedPage() instanceof DeckSectionPage) {
                ((DeckSectionPage) getSelectedPage()).cardManager.getConfig().setPileBy(null);
                ((DeckSectionPage) getSelectedPage()).cardManager.setHideFilters(true);
            }
        } else if (keyCode == Input.Keys.DPAD_RIGHT) {
            if (getSelectedPage() instanceof CatalogPage) {
                if (((CatalogPage) getSelectedPage()).cardManager.getConfig().getViewIndex() == 1)
                    ((CatalogPage) getSelectedPage()).setNextSelected();
            } else if (getSelectedPage() instanceof DeckSectionPage) {
                if (((DeckSectionPage) getSelectedPage()).cardManager.getConfig().getViewIndex() == 1)
                    ((DeckSectionPage) getSelectedPage()).setNextSelected();
            }
        } else if (keyCode == Input.Keys.DPAD_LEFT) {
            if (getSelectedPage() instanceof CatalogPage) {
                if (((CatalogPage) getSelectedPage()).cardManager.getConfig().getViewIndex() == 1)
                    ((CatalogPage) getSelectedPage()).setPreviousSelected();
            } else if (getSelectedPage() instanceof DeckSectionPage) {
                if (((DeckSectionPage) getSelectedPage()).cardManager.getConfig().getViewIndex() == 1)
                    ((DeckSectionPage) getSelectedPage()).setPreviousSelected();
            }
        } else if (keyCode == Input.Keys.DPAD_DOWN) {
            if (getSelectedPage() instanceof CatalogPage) {
                if (((CatalogPage) getSelectedPage()).cardManager.isContextMenuOpen()) {
                    ((CatalogPage) getSelectedPage()).cardManager.selectNextContext();
                } else {
                    if (((CatalogPage) getSelectedPage()).cardManager.getSelectedIndex() < 0)
                        ((CatalogPage) getSelectedPage()).setNextSelected();
                    else if (((CatalogPage) getSelectedPage()).cardManager.getConfig().getViewIndex() == 1)
                        ((CatalogPage) getSelectedPage()).setNextSelected(((CatalogPage) getSelectedPage()).cardManager.getConfig().getImageColumnCount());
                    else
                        ((CatalogPage) getSelectedPage()).setNextSelected();
                }
            } else if (getSelectedPage() instanceof DeckSectionPage) {
                if (((DeckSectionPage) getSelectedPage()).cardManager.isContextMenuOpen()) {
                    ((DeckSectionPage) getSelectedPage()).cardManager.selectNextContext();
                } else {
                    if (((DeckSectionPage) getSelectedPage()).cardManager.getSelectedIndex() < 0)
                        ((DeckSectionPage) getSelectedPage()).setNextSelected();
                    else if (((DeckSectionPage) getSelectedPage()).cardManager.getConfig().getViewIndex() == 1)
                        ((DeckSectionPage) getSelectedPage()).setNextSelected(((DeckSectionPage) getSelectedPage()).cardManager.getConfig().getImageColumnCount());
                    else
                        ((DeckSectionPage) getSelectedPage()).setNextSelected();
                }
            }
        } else if (keyCode == Input.Keys.DPAD_UP) {
            if (getSelectedPage() instanceof CatalogPage) {
                if (((CatalogPage) getSelectedPage()).cardManager.isContextMenuOpen()) {
                    ((CatalogPage) getSelectedPage()).cardManager.selectPreviousContext();
                } else {
                    if (((CatalogPage) getSelectedPage()).cardManager.getSelectedIndex() < 0)
                        ((CatalogPage) getSelectedPage()).setNextSelected();
                    else if (((CatalogPage) getSelectedPage()).cardManager.getConfig().getViewIndex() == 1)
                        ((CatalogPage) getSelectedPage()).setPreviousSelected(((CatalogPage) getSelectedPage()).cardManager.getConfig().getImageColumnCount());
                    else
                        ((CatalogPage) getSelectedPage()).setPreviousSelected();
                }
            } else if (getSelectedPage() instanceof DeckSectionPage) {
                if (((DeckSectionPage) getSelectedPage()).cardManager.isContextMenuOpen()) {
                    ((DeckSectionPage) getSelectedPage()).cardManager.selectPreviousContext();
                } else {
                    if (((DeckSectionPage) getSelectedPage()).cardManager.getSelectedIndex() < 0)
                        ((DeckSectionPage) getSelectedPage()).setNextSelected();
                    else if (((DeckSectionPage) getSelectedPage()).cardManager.getConfig().getViewIndex() == 1)
                        ((DeckSectionPage) getSelectedPage()).setPreviousSelected(((DeckSectionPage) getSelectedPage()).cardManager.getConfig().getImageColumnCount());
                    else
                        ((DeckSectionPage) getSelectedPage()).setPreviousSelected();
                }
            }
        } else if (keyCode == Input.Keys.BUTTON_A) {
            if (getSelectedPage() instanceof CatalogPage) {
                if (((CatalogPage) getSelectedPage()).cardManager.isContextMenuOpen())
                    ((CatalogPage) getSelectedPage()).cardManager.activateSelectedContext();
                else
                    ((CatalogPage) getSelectedPage()).cardManager.showMenu(true);
            } else if (getSelectedPage() instanceof DeckSectionPage) {
                if (((DeckSectionPage) getSelectedPage()).cardManager.isContextMenuOpen())
                    ((DeckSectionPage) getSelectedPage()).cardManager.activateSelectedContext();
                else
                    ((DeckSectionPage) getSelectedPage()).cardManager.showMenu(true);
            }
        } else if (keyCode == Input.Keys.BUTTON_B) {
            if (getSelectedPage() instanceof CatalogPage) {
                if (((CatalogPage) getSelectedPage()).cardManager.isContextMenuOpen()) {
                    ((CatalogPage) getSelectedPage()).cardManager.closeMenu();
                } else
                    return this.tabHeader.btnBack.trigger();
            } else if (getSelectedPage() instanceof DeckSectionPage) {
                if (((DeckSectionPage) getSelectedPage()).cardManager.isContextMenuOpen()) {
                    ((DeckSectionPage) getSelectedPage()).cardManager.closeMenu();
                } else
                    return this.tabHeader.btnBack.trigger();
            }
        } else if (keyCode == Input.Keys.BUTTON_Y) {
            if (getSelectedPage() instanceof CatalogPage) {
                if (!((CatalogPage) getSelectedPage()).cardManager.isContextMenuOpen()) {
                    if (((CatalogPage) getSelectedPage()).cardManager.getCurrentView().getSelectionCount() > 0) {
                        ((CatalogPage) getSelectedPage()).cardManager.getCurrentView().zoomSelected();
                    }
                } else {
                    ((CatalogPage) getSelectedPage()).cardManager.closeMenu();
                    if (((CatalogPage) getSelectedPage()).cardManager.getCurrentView().getSelectionCount() > 0) {
                        ((CatalogPage) getSelectedPage()).cardManager.getCurrentView().zoomSelected();
                    }
                }
            } else if (getSelectedPage() instanceof DeckSectionPage) {
                if (!((DeckSectionPage) getSelectedPage()).cardManager.isContextMenuOpen()) {
                    if (((DeckSectionPage) getSelectedPage()).cardManager.getCurrentView().getSelectionCount() > 0) {
                        ((DeckSectionPage) getSelectedPage()).cardManager.getCurrentView().zoomSelected();
                    }
                } else {
                    ((DeckSectionPage) getSelectedPage()).cardManager.closeMenu();
                    if (((DeckSectionPage) getSelectedPage()).cardManager.getCurrentView().getSelectionCount() > 0) {
                        ((DeckSectionPage) getSelectedPage()).cardManager.getCurrentView().zoomSelected();
                    }
                }
            }
        } else if (keyCode == Input.Keys.BUTTON_L1) {
            if (getSelectedPage() instanceof CatalogPage) {
                ((CatalogPage) getSelectedPage()).cardManager.closeMenu();
                int index = ((CatalogPage) getSelectedPage()).cardManager.getConfig().getViewIndex() == 1 ? 0 : 1;
                ((CatalogPage) getSelectedPage()).cardManager.setViewIndex(index);
            } else if (getSelectedPage() instanceof DeckSectionPage) {
                ((DeckSectionPage) getSelectedPage()).cardManager.closeMenu();
                int index = ((DeckSectionPage) getSelectedPage()).cardManager.getConfig().getViewIndex() == 1 ? 0 : 1;
                ((DeckSectionPage) getSelectedPage()).cardManager.setViewIndex(index);
            }
        }
        return true;
    }

    @Override
    public boolean keyUp(int keyCode) {
        if (keyCode == Input.Keys.ESCAPE) {
            return this.tabHeader.btnBack.trigger();
        }
        return super.keyUp(keyCode);
    }
}

