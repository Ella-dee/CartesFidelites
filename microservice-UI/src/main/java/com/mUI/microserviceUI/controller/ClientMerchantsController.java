package com.mUI.microserviceUI.controller;

import com.mUI.microserviceUI.beans.*;
import com.mUI.microserviceUI.beansDTO.AddShopDTO;
import com.mUI.microserviceUI.beansDTO.EditShopDTO;
import com.mUI.microserviceUI.exceptions.CannotAddException;
import com.mUI.microserviceUI.proxies.MicroserviceMerchantsProxy;
import com.mUI.microserviceUI.proxies.MicroserviceRewardsProxy;
import com.mUI.microserviceUI.proxies.MicroserviceUsersProxy;
import com.mUI.microserviceUI.utils.MapsUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;

import static com.mUI.microserviceUI.utils.MapsUtils.getDistanceDuration;
import static com.mUI.microserviceUI.utils.MapsUtils.setUpForGMaps;


/**
 * <h2>Controller linking with microservice-merchants</h2>
 */
@Controller
public class ClientMerchantsController {

    @Autowired
    private MicroserviceMerchantsProxy merchantsProxy;
    @Autowired
    private MicroserviceRewardsProxy rewardsProxy;
    @Autowired
    private MicroserviceUsersProxy usersProxy;


    /*
    ***************************************
    * Merchant List
    * *************************************
     */

    /**
     * <p>show list of possible merchants</p>
     * @param model
     * @return merchants.html
     */
    @GetMapping("/Marchands")
    public String listMerchants(Model model,HttpServletRequest request){
        HttpSession session = request.getSession();
        Integer userId = (Integer)request.getSession().getAttribute("loggedInUserId");
        List<MerchantBean> merchantBeanList = merchantsProxy.listMerchants();
        List<CategoryIconBean> icons = merchantsProxy.listIcons();
        for (MerchantBean m:merchantBeanList){
            //set up google map
           setUpForGMaps(m);
           //set up category icon
            m.getCategory().setIcon(merchantsProxy.getCategoryIcon(m.getCategory().getCategoryIcon()));
           //if a user is logged in, sets up DistanceMatrix attributes from the user's address
           if(userId!=null) {
               m.setDm(getDistanceDuration(usersProxy.showUser(userId),m));
           }
        }
        model.addAttribute("merchants", merchantBeanList);
        model.addAttribute("userId", userId);
        return "merchants";
    }
    /*
     **************************************
     * Merchant register
     * ************************************
     */


    /**
     * <p>Page that displays a form to register a merchant</p>
     * @param model attribute passed to jsp page
     * @return login page
     */
    @GetMapping("/Marchands/nouvelle-boutique")
    public String registerPage(Model model, HttpServletRequest request) {
        AddShopDTO addShopDTO = new AddShopDTO();
        HttpSession session = request.getSession();
        List<CategoryBean> allCats = merchantsProxy.listCategories();
        model.addAttribute("cats", allCats);
        model.addAttribute("sessionId", session.getAttribute("loggedInUserId"));
        model.addAttribute("addShopDTO", addShopDTO);
        return "register-shop";
    }
//TODO voir comment gérer les catégories (prédéfinies? liste déroulante?
    /**
     * <p>Process called after the submit button is clicked on register page</p>
     * @param addShopDTO merchant being created by DTO
     * @param model attribute passed to jsp page
     * @return page to show depending on result of process
     */
    @PostMapping("/Marchands/add-shop")
    public String saveMerchant(@ModelAttribute("addShopDTO")AddShopDTO addShopDTO, ModelMap model) {
        String toBeReturned;
        String[] longAddress = addShopDTO.getAddress().split(",", addShopDTO.getAddress().length());
        String address=longAddress[0];
        String city = longAddress[1].replaceAll("\\s+","");

        List<CategoryBean> allCats = merchantsProxy.listCategories();
        if (!city.equals("Puteaux")){
            model.addAttribute("cats", allCats);
            model.addAttribute("errorMessage", "Cette application supporte actuellement les commerces de Puteaux uniquement");
            return "register-shop";
        }
        if (Integer.parseInt(addShopDTO.getPoints())>10){
            model.addAttribute("cats", allCats);
            model.addAttribute("errorMessage", "Vous devez attribuer un maximum de 10 points à une carte fidélité");
            return "register-shop";
        }
        try {
            addShopDTO.setMaxPoints(Integer.parseInt(addShopDTO.getPoints()));
            ArrayList<String>longlat=MapsUtils.geocodeFromString(addShopDTO.getAddress());
            addShopDTO.setLongitude(longlat.get(0));
            addShopDTO.setLatitude(longlat.get(1));
            addShopDTO.setAddress(address);
            MerchantBean merchantToRegister = merchantsProxy.addShop(addShopDTO);
            toBeReturned = "redirect:/Marchands/"+merchantToRegister.getId();
        }
        catch (Exception e){
            e.printStackTrace();
            if(e instanceof CannotAddException){
                model.addAttribute("errorMessage", e.getMessage());
            }
            model.addAttribute("cats", allCats);
            toBeReturned = "register-shop";
        }
        return toBeReturned;
    }

    /**
     * shows details of particular merchant with its id
     * @param shopId
     * @param model
     * @return merchant-details.html
     */
    @RequestMapping("/Marchands/{shopId}")
    public String merchantDetails(@PathVariable Integer shopId, Model model, HttpServletRequest request){
        HttpSession session = request.getSession();
        MerchantBean merchant = merchantsProxy.showShop(shopId);
        setUpForGMaps(merchant);
        //prepare reward card for merchant
        RewardBean rewardCard = new RewardBean();
        rewardCard.setIdMerchant(merchant.getId());
        rewardCard.setMaxPoints(merchant.getMaxPoints());
        Integer userId = (Integer)request.getSession().getAttribute("loggedInUserId");
        rewardCard.setIdUser(userId);
        for(RewardBean r:rewardsProxy.listRewards()){
            if(r.getIdUser() == rewardCard.getIdUser()){
                if (r.getIdMerchant() == rewardCard.getIdMerchant()){
                    model.addAttribute("errorMessage", "Vous avez déjà une carte fidélité chez ce marchand :)");
                }
            }
        }
        //if a user is logged in, sets up DistanceMatrix attributes from the user's address
        if(userId!=null) {
            merchant.setDm(getDistanceDuration(usersProxy.showUser(userId),merchant));
        }
        //TODO total des récompenses distribuées???
        //if user in session is owner of shops, show the users with cards of the shops
        if(merchant.getUserId() == userId){
            //search if shop has active fidelity cards
            List<RewardBean>rewards = rewardsProxy.listRewards();
            List<RewardBean>shopRewards = new ArrayList<>();
            for (RewardBean r:rewards){
                if(r.getIdMerchant()==shopId){
                    shopRewards.add(r);
                }
            }
            //search number of total rewards in waiting
            int rewardsInWaiting = 0;
            for(RewardBean r: shopRewards){
                int cardRewards = r.getRewardsNbr();
                rewardsInWaiting += cardRewards;
            }
            model.addAttribute("cards", shopRewards);
            model.addAttribute("rewardsTot", rewardsInWaiting);
        }
        model.addAttribute("merchant", merchant);
        model.addAttribute("rewardCard", rewardCard);
        model.addAttribute("sessionId", session.getAttribute("loggedInUserId"));
        return "shop-profile";
    }
    /**
     * shows lists of shop with its owner id
     * @param model
     * @return merchant-details.html
     */
    @RequestMapping("/Marchands/MesBoutiques")
    public String shopListByOwner(Model model, HttpServletRequest request){
        List<MerchantBean> allShops = merchantsProxy.listMerchants();
        Integer loggedInUserId = (Integer)request.getSession().getAttribute("loggedInUserId");
        List<MerchantBean> list = new ArrayList<>();
        for(MerchantBean shop:allShops){
            if(loggedInUserId.equals(shop.getUserId())){
                //set up google map
                setUpForGMaps(shop);
                //set up category icon
                shop.getCategory().setIcon(merchantsProxy.getCategoryIcon(shop.getCategory().getCategoryIcon()));
                list.add(shop);
            }
        }
        model.addAttribute("shopList", list);
        return "myshops";
    }
    /*
     **************************************
     * Edit Merchant
     * ************************************
     */
    @GetMapping("/Marchands/edit/{shopId}")
    public String editUserPage(@PathVariable Integer shopId, Model model, HttpServletRequest request){
        HttpSession session = request.getSession();
        MerchantBean shop = merchantsProxy.showShop(shopId);
        //session is always checked, here check if user editing profile is owner of profile
        if(!session.getAttribute("loggedInUserId").equals(shop.getUserId())){
            System.out.println("User trying to edit shop profile is not the owner of the shop");
            System.out.println("User is: [id:"
                    +session.getAttribute("loggedInUserId")+ ", email:"
                    +session.getAttribute("loggedInUserEmail")+", role:"
                    +session.getAttribute("loggedInUserRole")+"]");
            return "redirect:/Accueil";
        }
        model.addAttribute("shop", shop);
        model.addAttribute("shopId", shop.getId());
        return "edit-shop";
    }

    @RequestMapping("/Marchands/edit")
    public String editUser(@ModelAttribute("shop") EditShopDTO editShopDTO, ModelMap model, HttpServletRequest request){
        String toBeReturned;
        HttpSession session = request.getSession();
        MerchantBean shop = merchantsProxy.showShop(editShopDTO.getId());
        if(!editShopDTO.getEmail().isEmpty()){
            shop.setEmail(editShopDTO.getEmail());
        }if(!editShopDTO.getAddress().isEmpty()){
            shop.setAddress(editShopDTO.getAddress());
            ArrayList<String>longlat=MapsUtils.geocodeFromString(shop.getAddress());
            shop.setLongitude(longlat.get(0));
            shop.setLatitude(longlat.get(1));
        }if(!editShopDTO.getMerchantName().isEmpty()){
            shop.setMerchantName(editShopDTO.getMerchantName());
        }if(!editShopDTO.getMaxPoints().isEmpty()){
            shop.setMaxPoints(Integer.parseInt(editShopDTO.getMaxPoints()));
        }
        try{
            MerchantBean shopToEdit = merchantsProxy.editShop(shop);
            toBeReturned = "redirect:/Marchands/"+editShopDTO.getId();
        }catch (Exception e){
            e.printStackTrace(); //TODO revoir message d'erreur
            model.addAttribute("errorMessage", "ERREUR ERREUR");
            toBeReturned = "redirect:/Marchands/edit/"+editShopDTO.getId();
        }
        return toBeReturned;
    }



    /*
     **************************************
     * Merchant delete
     * ************************************
     */

    /**
     * <p>deletes a merchant when merchant clicks on suppress button</p>
     * @param id
     * @return url depending on function result
     */
    @RequestMapping(value = "/Marchands/delete/{id}", method = RequestMethod.POST)
    public String deleteMerchant(@PathVariable("id") Integer id, HttpServletRequest request){
        HttpSession session = request.getSession();
        Integer userId = (Integer) session.getAttribute("loggedInUserId");
        merchantsProxy.deleteShop(id);
        return "redirect:/Marchands/MesBoutiques";
    }


    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ModelAndView handleMissingParams(MissingServletRequestParameterException ex){
        return new ModelAndView("redirect:/Accueil");
    }

}